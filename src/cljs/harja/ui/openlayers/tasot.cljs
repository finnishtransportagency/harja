(ns harja.ui.openlayers.tasot
  "Määrittelee karttatason kaltaisen protokollan")

(defprotocol Taso
  (aseta-z-index [this z-index]
                 "Palauttaa uuden version tasosta, jossa zindex on asetettu")
  (extent [this] "Palauttaa tason geometrioiden extentin [minx miny maxx maxy]")
  (selitteet [this] "Palauttaa tällä tasolla olevien asioiden selitteet"))

;; Laajenna vektorit olemaan tasoja
(extend-protocol Taso
  PersistentVector
  (aseta-z-index [this z-index]
    (with-meta this
      (merge (meta this)
             {:zindex z-index})))
  (extent [this]
    (-> this meta :extent))
  (selitteet [this]
    (-> this meta :selitteet)))
