(ns harja.kyselyt.turvallisuuspoikkeamat
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/turvallisuuspoikkeamat.sql"
  {:positional? true})

(def turvallisuuspoikkeama-xf
  (comp (map #(konv/string->keyword % :korjaavatoimenpide_tila))
        (map konv/alaviiva->rakenne)
        (geo/muunna-pg-tulokset :sijainti)
        (map #(konv/array->set % :tyyppi))
        (map #(assoc % :vaaralliset-aineet (into #{} (remove nil?
                                                             [(when (:vaarallisten-aineiden-kuljetus %)
                                                                :vaarallisten-aineiden-kuljetus)
                                                              (when (:vaarallisten-aineiden-vuoto %)
                                                                :vaarallisten-aineiden-vuoto)]))))
        (map #(dissoc % :vaarallisten-aineiden-kuljetus))
        (map #(dissoc % :vaarallisten-aineiden-vuoto))
        (map #(konv/string-set->keyword-set % :tyyppi))
        (map #(konv/array->set % :vahinkoluokittelu))
        (map #(konv/string-set->keyword-set % :vahinkoluokittelu))
        (map #(konv/string->keyword % :vahingoittuneetruumiinosat))
        (map #(konv/string->keyword % :vammat))
        (map #(konv/string->keyword % :vakavuusaste))
        (map #(konv/string->keyword % :tila))
        (map #(konv/string->keyword % :vaylamuoto))
        (map #(konv/string->keyword % :tyontekijanammatti))
        (map #(konv/string-polusta->keyword % [:kommentti :tyyppi]))))

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))
