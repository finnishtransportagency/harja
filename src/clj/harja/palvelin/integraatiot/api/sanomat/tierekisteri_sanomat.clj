(ns harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat)

(defn luo-tierekisteriosoite [parametrit]
  (into {} (filter val (zipmap [:numero :aet :aosa :let :losa :ajr :puoli :alkupvm]
                               (map (partial get parametrit) ["numero" "aet" "aosa" "let" "losa" "ajr" "puoli" "alkupvm"])))))

(defn muunna-tietolajin-hakuvastaus [vastausdata ominaisuudet]
  {:tietolajit [(dissoc
                  (dissoc (assoc-in vastausdata [:tietolaji :ominaisuudet]
                                    (map (fn [o]
                                           {:ominaisuus o})
                                         ominaisuudet)) :onnistunut)
                  :tietueet)]})

(defn muunna-tietolajien-hakuvastaus [vastausdata]
  {:tietolajit (remove #(empty? (get-in % [:tietolaji :ominaisuudet]))
                       (map (fn [t] {:tietolaji (assoc (:tietolaji t)
                                                  :ominaisuudet
                                                  (map (fn [o] {:ominaisuus o})
                                                       (get-in t [:tietolaji :ominaisuudet])))}) vastausdata))})
