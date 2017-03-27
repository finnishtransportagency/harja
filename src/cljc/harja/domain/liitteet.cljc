(ns harja.domain.liitteet)

(defn tarkista-liite [liite]
  (let [max-koko-tavuina 32000000
        mime-whitelist #{;; Kuvat
                         "image/png"
                         "image/tiff"
                         "image/jpeg"
                         ;; Arkistot
                         "application/zip"
                         "application/x-compressed"
                         "application/x-zip-compressed"
                         ; MS Office
                         "application/msword" ; .doc ym.
                         "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ; .docx
                         "application/vnd.openxmlformats-officedocument.wordprocessingml.template" ; .dotx
                         "application/excel" ; .xls ym.
                         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ; .xlsx
                         "application/vnd.openxmlformats-officedocument.spreadsheetml.template" ; .xltx
                         "application/vnd.ms-powerpoint" ; .ppt ym.
                         "application/vnd.openxmlformats-officedocument.presentationml.presentation" ; .pptx
                         "application/vnd.openxmlformats-officedocument.presentationml.template" ; .ppsx
                         ;; OpenDocument (LibreOffice / OpenOffice)
                         "application/vnd.oasis.opendocument.text" ; .odt
                         "application/vnd.oasis.opendocument.text.template" ; .ott
                         "application/vnd.oasis.opendocument.spreadsheet" ; .ods
                         "application/vnd.oasis.opendocument.spreadsheet-template" ; .ots
                         "application/vnd.oasis.opendocument.presentation" ; .odp
                         "application/vnd.oasis.opendocument.presentation-template" ; .otp
                         ;; Muut tekstidokumentit
                         "application/rtf"
                         "text/rtf"
                         "application/xml"
                         "text/xml"
                         "application/pdf"
                         "text/plain"}]
    (if (and (:koko liite) (:tyyppi liite))
      (if (> (:koko liite) max-koko-tavuina)
        {:hyvaksytty false :viesti (str "Liite on liian suuri (sallittu koko " max-koko-tavuina " tavua).")}
        (if (nil? (mime-whitelist (:tyyppi liite)))
          {:hyvaksytty false :viesti (str "Tiedostotyyppi (" (:tyyppi liite) ") ei ole sallittu.")}
          {:hyvaksytty true :viesti nil}))
      {:hyvaksytty false :viesti "Järjestelmä ei voi käsitellä tiedostoa."})))