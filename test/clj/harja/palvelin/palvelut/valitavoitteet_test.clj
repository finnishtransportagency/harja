(ns harja.palvelin.palvelut.valitavoitteet-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.valitavoitteet :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :as testi]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae (component/using
                               (->Valitavoitteet)
                               [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest urakan-valitavoitteiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-valitavoitteet +kayttaja-jvh+ (hae-oulun-alueurakan-2014-2019-id))]

    (log/debug "Vastaus: " vastaus)
    (is (>= (count vastaus) 4))))


(deftest urakan-valitavoitteiden-haku-toimii
  (let [vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :tallenna-valtakunnalliset-valitavoitteet
                  +kayttaja-jvh+
                  {:valitavoitteet [{:id -2, :nimi "Kertaluontoinen",
                                     :takaraja (c/to-date (t/plus (t/now) (t/years 5))),
                                     :tyyppi :kertaluontoinen, :urakkatyyppi :hoito,
                                     :takaraja-toistopaiva nil, :takaraja-toistokuukausi nil}
                                    {:id -5, :nimi "Sepon mökkitien vuosittainen auraus",
                                     :takaraja nil, :tyyppi :toistuva,
                                     :urakkatyyppi :hoito, :takaraja-toistopaiva 1,
                                     :takaraja-toistokuukausi 7}]})]

    (log/debug "Vastaus: " vastaus)
    (is (= (count vastaus) 2))
    (is (= (count (filter #(= (:tyyppi %) :kertaluontoinen) vastaus)) 1))
    (is (= (count (filter #(= (:tyyppi %) :toistuva) vastaus)) 1))
    (u (str "DELETE FROM valitavoite WHERE valtakunnallinen_valitavoite IS NOT NULL"))
    (u (str "DELETE FROM valitavoite WHERE urakka IS NULL"))))
