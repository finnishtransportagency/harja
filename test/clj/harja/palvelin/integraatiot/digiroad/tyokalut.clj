(ns harja.palvelin.integraatiot.digiroad.tyokalut)

(def +digiroad-url+ "http://localhost:1234/")
(def +kaistojen-haku-url+ (str +digiroad-url+ "lanes/lanes_in_range"))

(def +onnistunut-digiroad-kaistojen-hakuvastaus+
  "[{\"roadNumber\":4,\"roadPartNumber\":101,\"track\":1,\"startAddrMValue\":0,\"endAddrMValue\":170,\"laneCode\":12,\"laneType\":2},{\"roadNumber\":4,\"roadPartNumber\":101,\"track\":1,\"startAddrMValue\":0,\"endAddrMValue\":170,\"laneCode\":11,\"laneType\":1}]")
