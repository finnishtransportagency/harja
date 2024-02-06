(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallysteen-korjausluokat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.paallysteen-korjausluokat :as paallysteen-korjausluokat]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-korjausluokat-entry [db rivi]
    (paallysteen-korjausluokat/tallenna-paallysteen-korjausluokka! db
      {:tie (:alkusijain rivi)
       :aosa (:alkusijai0 rivi)
       :aet (:alkusijai1 rivi)
       :losa (:loppusija0 rivi)
       :let (:loppusija1 rivi)
       :korjausluokka (:paallystee rivi)}))

;; Voit testata päällysteen korjausluokkien shapefilen kanta-ajoa vaihtamalla namespacen replissä tähän tiedostoon
;; ja kutsumalla (vie-korjausluokat-kantaan db "<polku shapefileen, esim. file:///Users/<user>/Downloads/paallysteen_korjausluokka/paallysteen_korjausluokkaLine.shp")
(defn vie-korjausluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan päällysteen korjausluokat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (paallysteen-korjausluokat/tuhoa-paallysteen-korjausluokat! db)
        (doseq [rivi (shapefile/tuo shapefile)]
          (vie-korjausluokat-entry db rivi))))
    (throw (Exception. (format "Päällysteen korjausluokka geometrioiden tiedostopolkua %s ei löydy konfiguraatiosta. Tuontia ei suoriteta." shapefile)))))
