import Current_module as cc
import time as t

cc.setup_instruments()

cc.run_at_voltage(cc.psu, cc.dmm, 3)

t.sleep(2)

cc.run_at_voltage(cc.psu, cc.dmm, 2.5)








