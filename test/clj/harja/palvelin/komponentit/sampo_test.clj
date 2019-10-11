(ns ^:integraatio harja.palvelin.komponentit.sampo-test
  (:require [harja.palvelin.asetukset :as asetukset]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :as sampo-tk]
            [harja.palvelin.integraatiot.sampo.sampo-komponentti :as sampo]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.pvm :as pvm]




            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tk]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.kyselyt.konversio :as konv]
            [clojure.test :refer :all]
            [clojure.string :as clj-str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout put! alts!! chan poll!]]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [slingshot.slingshot :refer [try+]]

            [harja.palvelin.main :as sut]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sonja-sahkoposti]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]))

(defonce asetukset {:sonja {:url "tcp://localhost:61617"
                            :kayttaja ""
                            :salasana ""
                            :tyyppi :activemq}
                    :sampo {:lahetysjono-sisaan sampo-tk/+lahetysjono-sisaan+
                            :kuittausjono-sisaan sampo-tk/+kuittausjono-sisaan+
                            :lahetysjono-ulos sampo-tk/+lahetysjono-ulos+
                            :kuittausjono-ulos sampo-tk/+kuittausjono-ulos+
                            :paivittainen-lahetysaika nil}})

(defonce ai-port 8162)

(def ^:dynamic *sonja-yhteys* nil)

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db ds
                        :http-palvelin (testi-http-palvelin)
                        :sonja (component/using
                                 (sonja/luo-oikea-sonja (:sonja asetukset))
                                 [:db])
                        :integraatioloki (component/using (integraatioloki/->Integraatioloki nil)
                                                          [:db])
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :sampo (component/using (let [sampo (:sampo asetukset)]
                                                  (sampo/->Sampo (:lahetysjono-sisaan sampo)
                                                                 (:kuittausjono-sisaan sampo)
                                                                 (:lahetysjono-ulos sampo)
                                                                 (:kuittausjono-ulos sampo)
                                                                 (:paivittainen-lahetysaika sampo)))
                                                [:sonja :db :integraatioloki :pois-kytketyt-ominaisuudet])
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat)
                                                [:db])))))
  ;; aloita-sonja palauttaa kanavan.
  (binding [*sonja-yhteys* (go
                             (<! (sut/aloita-sonja jarjestelma)))]
    (testit))
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest merkataan-budjettitavotteita-likaiseksi-ja-lähetetään-ne-Sampoon
  (let [{urakka-id :id alkupvm :alkupvm} (first (q-map (str "SELECT id, alkupvm
                                                             FROM   urakka
                                                             WHERE  nimi = 'Ivalon MHU testiurakka (uusi)'")))
        urakan-aloitusvuosi (pvm/vuosi alkupvm)
        tyo {:urakka-id urakka-id
             :toimenpide-avain :talvihoito
             :ajat (into []
                         (mapcat (fn [hoitokausi]
                                   (let [kuukaudet (case hoitokausi
                                                     1 (range 10 13)
                                                     5 (range 1 10)
                                                     (range 1 13))
                                         vuosi (+ urakan-aloitusvuosi (dec hoitokausi))]
                                     (map (fn [kuukausi]
                                            {:kuukausi kuukausi
                                             :vuosi vuosi})
                                          kuukaudet)))
                                 (range 1 6)))
             :summa 1000}
        toimenpideinstanssi (ffirst (q (str "SELECT id
                                             FROM toimenpideinstanssi
                                             WHERE urakka = " urakka-id " AND
                                                   toimenpide = (SELECT id FROM toimenpidekoodi WHERE taso = 3 AND koodi='23104')")))
        vastaus (bs/tallenna-kiinteahintaiset-tyot (:db jarjestelma) +kayttaja-jvh+ tyo)
        kustannussuunnitelmat (q-map (str "SELECT ks.*
                                           FROM kustannussuunnitelma ks
                                             JOIN maksuera m ON m.numero = ks.maksuera
                                             JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                                           WHERE m.tyyppi = 'kokonaishintainen' AND
                                                 tpi.id = " toimenpideinstanssi))]
    (is (:onnistui? vastaus) "Kiinteähintaisen työn tallentaminen epäonnistui")
    (is (= 1 (count kustannussuunnitelmat)))
    (is (-> kustannussuunnitelmat first :likainen))))
