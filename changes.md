# OmegaT -- cApStAn build

## Version 5.7.4

* M68: Bugfix for unexpected auto-population behaviour
* M85: Wait for post-processing scripts to finish before compile
* M85: Patch to add a second option to commit target files without re-creating them
* M96: Custom configuration folder, new vendor property in preferences, etc.

## Version 5.7.3_0 (57b1bb571)

* Online help (F1) points to cApStAn's OmegaT guides
* New option in Prefs: create target files also commits them
* New option in Prefs: close project creates target files
* JSON stats file will not include timestamp
* BUG#1225: Issues dialog gains focus when called and come to the front
* Enforced translations are locked
* Removed function "Commit Source Files"
* RFE#1754: User preference for maximum number of TM matches displayed
* Updated updateConfigBundle.groovy scripts

## Version 5.7.2 (a978d82ee)

* 1164: working TM not loaded to memory
* 1690/1695: commit JSON stats file
* 1213: match ranking
* 1176: COMPILE event fire after target commit
* 1672: hide tag-only segments
