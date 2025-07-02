import pyvisa
import time
import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import os 

#set up instruments
def setup_instruments():
    rm = pyvisa.ResourceManager()
    
    # Updated addresses based on your system
    dmm_addr = 'USB0::0x0957::0x0607::my47026696::0::INSTR'
    psu_addr = 'USB0::0x1AB1::0x0E11::dp8c163452166::0::INSTR'
    
    available_resources = rm.list_resources()
    print(f"🔍 Available VISA resources: {available_resources}")

    # Open and test DMM first with faster timeouts
    dmm = None
    if dmm_addr in available_resources:
        try:
            dmm = rm.open_resource(dmm_addr)
            dmm.timeout = 3000  # Reduced timeout to 3 seconds
            time.sleep(0.2)  # Reduced wait time
            print("DMM ID:", dmm.query("*IDN?"))
            # Quick reset
            dmm.write("*CLS")
        except Exception as e:
            print(f"❌ DMM connection failed: {e}")
            dmm = None
    else:
        print(f"❌ DMM not found in available resources: {dmm_addr}")

    time.sleep(0.5)  # Reduced wait time

    # Now open PSU with faster connection
    psu = None
    if psu_addr in available_resources:
        try:
            psu = rm.open_resource(psu_addr)
            psu.timeout = 3000  # Reduced timeout to 3 seconds
            time.sleep(0.2)  # Reduced wait time
            print("Power Supply ID:", psu.query("*IDN?"))
            
            # Quick initialization
            psu.write("*CLS")
            psu.write(":INST CH1")
            psu.write(":OUTP OFF")
            psu.write(":VOLT 0")
            time.sleep(0.2)  # Reduced wait time
            
        except Exception as e:
            print(f"⚠️ Power Supply connection failed: {e}")
            psu = None
    else:
        print(f"⚠️ Power Supply not found in available resources: {psu_addr}")
        print("💡 Check if PSU is connected and powered on")

    return psu, dmm


def run_test(psu, dmm, voltage, label="Standard", nplc=0.1, sample_count=100):
    psu.write(":INST CH1")
    psu.write(f":VOLT {voltage:.2f}")
    psu.write(":OUTP ON")  # Turn on the power supply output
    print(f"\n Running '{label}' test at {voltage} V...")

    try:
        dmm.timeout = int(sample_count * nplc * 1000 * 2)  # Reduced timeout calculation
        dmm.write("*CLS")
        dmm.write(":CONF:CURR:DC")

        dmm.write(f":CURR:DC:NPLC {nplc}")  # Reduced NPLC for faster measurement
        dmm.write(f":SAMP:COUN {sample_count}")  # Reduced sample count

        dmm.write(":TRIG:SOUR IMM")
        dmm.write(":INIT")

        print(f" DMM initialized for {sample_count} samples, waiting...")
        time.sleep(sample_count * 0.01)  # Reduced wait time per sample

        raw_data = dmm.query(":FETC?")
        
        # Clean the data - remove any extra text and split properly
        raw_data = raw_data.strip()
        if ',' in raw_data:
            # Split by comma and clean each value
            readings = []
            for value in raw_data.split(','):
                try:
                    # Remove any non-numeric characters except decimal point, minus, and E
                    clean_value = ''.join(c for c in value if c.isdigit() or c in '.-Ee')
                    if clean_value:
                        readings.append(float(clean_value))
                except ValueError:
                    print(f"⚠️ Skipping invalid value: {value}")
                    continue
        else:
            # Single value
            try:
                clean_value = ''.join(c for c in raw_data if c.isdigit() or c in '.-Ee')
                readings = [float(clean_value)] if clean_value else []
            except ValueError:
                print(f"⚠️ Invalid single value: {raw_data}")
                readings = []

        return {
            "label": label,
            "voltage": voltage,
            "readings": readings
        }

    except Exception as e:
        print(f" Error during measurement: {e}")
        return {
            "label": label,
            "voltage": voltage,
            "readings": []
        }

def detect_beep(readings, label, threshold=0.005, min_gap=10):
    # Lowered threshold from 0.02 to 0.005 → detects ~5 mA spikes
    above = np.array(readings) > threshold
    spike_indices = []
    i = 0
    while i < len(above):
        if above[i]:
            spike_indices.append(i)
            i += min_gap
        else:
            i += 1

    num_spikes = len(spike_indices)

    print(f"📊 {label}: Detected {num_spikes} spike(s)")

    if label == "Double Beep":
        detected = num_spikes >= 2
        print("✅ Double Beep Detected!" if detected else "❌ Double Beep NOT detected.")
        return detected

    if label == "Triple Beep":
        detected = num_spikes >= 3
        print("✅ Triple Beep Detected!" if detected else "❌ Triple Beep NOT detected.")
        return detected

# plot results 
def plot_results(results):
    plt.figure(figsize=(10, 6))
    for res in results:
        t = [i * (1/1000) for i in range(len(res['readings']))]
        y = [x * 1000 for x in res['readings']]
        plt.plot(t, y, label=f"{res['label']} ({res['voltage']} V)")
    plt.xlabel("Time (s)")
    plt.ylabel("Current (mA)")
    plt.title("Current Over Time - All Tests")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.show()

#export to csv
def export_summary_to_csv(voltage_main, max_current, double_detected, triple_detected, filename="summary_results.csv"):
    summary_row = {
        "Voltage (V)": voltage_main,
        "Max Current (mA)": round(max_current * 1000, 3),
        "Double Beep": "PASS" if double_detected else "FAIL",
        "Triple Beep": "PASS" if triple_detected else "FAIL"
    }

    file_exists = os.path.isfile(filename)

    df = pd.DataFrame([summary_row])
    df.to_csv(filename, mode='a', header=not file_exists, index=False)

    print(f"\n📄 Summary appended to '{os.path.abspath(filename)}'")

def run_beep_sequence(psu, dmm, v_main, v_double, v_triple):
    if psu is None:
        print("❌ Cannot run test — Power Supply not connected.")
        return

    psu.write(":OUTP ON")

    result_main = run_test(psu, dmm, v_main, label="Main Test")
    r = result_main['readings']
    max_current = max(r) if r else 0

    if not r:
        print("❌ No current readings collected. Skipping analysis.")
        return

    print(f"\n Main Test ({v_main} V):")
    print(f"Min: {min(r)*1000:.3f} mA | Max: {max(r)*1000:.3f} mA | Avg: {sum(r)/len(r)*1000:.3f} mA")

    result_double = run_test(psu, dmm, v_double, label="Double Beep")
    double_detected = detect_beep(result_double['readings'], "Double Beep")

    result_triple = run_test(psu, dmm, v_triple, label="Triple Beep", nplc=1, sample_count=600)
    triple_detected = detect_beep(result_triple['readings'], "Triple Beep", threshold=0.005, min_gap=10)


    plot_results([result_main, result_double, result_triple])

def main():
    psu = None
    dmm = None
    try:
        psu, dmm = setup_instruments()

        if dmm is None:
            print("❌ DMM not available — skipping tests.")
            return

        print("\n Enter test voltages for automated run:")
        v_main = float(input(" MAIN test voltage: "))
        v_double = float(input(" DOUBLE BEEP test voltage: "))
        v_triple = float(input(" TRIPLE BEEP test voltage: "))

        run_beep_sequence(psu, dmm, v_main, v_double, v_triple)

    except ValueError:
        print(" Invalid input. Please enter numeric voltages.")

    except Exception as e:
        print(f" Error: {e}")

    finally:
        print("\n🔌 Closing instrument connections...")
        try:
            if dmm:
                dmm.close()
                print("✅ DMM closed.")
            if psu:
                psu.close()
                print("✅ PSU closed.")
        except Exception as e:
            print(f"⚠️ Error during close: {e}")


if __name__ == "__main__":
    main()