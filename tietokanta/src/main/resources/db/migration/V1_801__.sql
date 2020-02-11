-- MHU-urakoissa on myös neljä omaa sanktiolajiaan:
ALTER TYPE sanktiolaji ADD VALUE 'lupaussanktio';
ALTER TYPE sanktiolaji ADD VALUE 'vaihtosanktio';
ALTER TYPE sanktiolaji ADD VALUE 'testikeskiarvo-sanktio';
ALTER TYPE sanktiolaji ADD VALUE 'tenttikeskiarvo-sanktio';