This is the library that manages the training and classification part of Hjerte.
It was separated from the GUI part as it enables to work on each part (the GUI and the Lib) separately and not mixing domains.
At some point the API will be more formal, but up to now this is just another Jar file.

Hjertelib uses a HMM and not another ML algorithm, as there is a need to make its classification explainable to Hjerte users, who are normally MD and other heathcare staff who have to base their decisions on information based on patient's physiology, not on weak signals.

Nb: This source of HjerteLib is provided for convenience, but it is not the most recent, nor the most stable.



