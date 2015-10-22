(ns harja.liitteet)

(defn tarkista-liite [liite]
  (let [max-koko-tavuina 16000000
        mime-whitelist #{"image/png"
                         "image/tiff"
                         "image/jpeg"
                         "application/zip"
                         "application/msword"
                         "application/excel"
                         "application/pdf"}]
    (if (> (:koko liite) max-koko-tavuina)
      {:hyvaksytty false :viesti (str "Liite on liian suuri (sallittu koko " max-koko-tavuina " tavua).")}
      (if (nil? (mime-whitelist (:tyyppi liite)))
        {:hyvaksytty false :viesti "Tiedostotyyppi ei ole sallittu."}
        {:hyvaksytty true :viesti nil}))))