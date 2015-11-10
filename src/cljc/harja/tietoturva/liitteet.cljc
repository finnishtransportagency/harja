(ns harja.tietoturva.liitteet)

(defn tarkista-liite [liite]
  (let [max-koko-tavuina 32000000
        mime-whitelist #{"image/png"
                         "image/tiff"
                         "image/jpeg"
                         "application/zip"
                         "application/x-compressed"
                         "application/x-zip-compressed"
                         "application/msword"
                         "application/excel"
                         "application/rtf"
                         "application/xml"
                         "text/rtf"
                         "text/xml"
                         "application/pdf"
                         "application/vnd.oasis.opendocument.text"
                         "application/vnd.oasis.opendocument.spreadsheet"
                         "text/plain"}]
    (if (and (:koko liite) (:tyyppi liite))
      (if (> (:koko liite) max-koko-tavuina)
        {:hyvaksytty false :viesti (str "Liite on liian suuri (sallittu koko " max-koko-tavuina " tavua).")}
        (if (nil? (mime-whitelist (:tyyppi liite)))
          {:hyvaksytty false :viesti (str "Tiedostotyyppi (" (:tyyppi liite) ") ei ole sallittu.")}
          {:hyvaksytty true :viesti nil}))
      {:hyvaksytty false :viesti "Järjestelmä ei voi käsitellä tiedostoa."})))