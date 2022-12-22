(ns harja.palvelin.integraatiot.yha.yha-yhteiset
  "Sisältää YHA:n päällystykseen ja paikkaukseen liittyviä yhteisiä toimintoja.")



(defn yha-otsikot
  "Muotoilee YHA:n api-keyn headerin"
  [api-key json?]
  (merge {"Content-Type" (if json?
                           "application/json"
                           "text/xml; charset=utf-8")}
         (when api-key {"x-api-key" api-key})))
