(ns ^:integraatio harja.palvelin.komponentit.sampo-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout put! alts!! chan poll!]]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sampo
             [tyokalut :as sampo-tk]
             [sampo-komponentti :as sampo]
             [vienti :as vienti]]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.palvelin.main :as sut]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.integraatiot.sonja.tyokalut :as s-tk]
            [harja.data.hoito.kustannussuunnitelma :as ks-data]))

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
  (<!! (sut/aloita-sonja jarjestelma))
  ;; Merkataan kaikki kannassa oleva testidata lähetetyksi ennen testien ajoa ja purgetaan jono.
  ;; Mikäli joku testi riippuu siitä, että testidataa ei olla merkattu lähetetyksi, tämä aiheuttaa
  ;; sen epäonnistumisen
  (vienti/aja-paivittainen-lahetys (:sonja jarjestelma) (:integraatioloki jarjestelma) (:db jarjestelma) (get-in asetukset [:sampo :lahetysjono-ulos]))
  (s-tk/sonja-jolokia-jono (get-in asetukset [:sampo :lahetysjono-ulos]) nil :purge)
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest merkataan-budjettitavotteita-likaiseksi-ja-lähetetään-ne-Sampoon
  (let [{urakka-id :id} (first (q-map (str "SELECT id, alkupvm
                                            FROM   urakka
                                            WHERE  nimi = 'Ivalon MHU testiurakka (uusi)'")))
        kiinteahintaiset-data (ks-data/tallenna-kiinteahintaiset-tyot-data urakka-id)
        jhk-data (ks-data/tallenna-johto-ja-hallintokorvaus-data urakka-id)
        kustannusarvioitu-data (ks-data/tallenna-kustannusarvioitu-tyo-data-juuri-alkaneelle-urakalle urakka-id)

        kokonaishintainen-maksuerantyyppi #{:toimenpiteen-maaramitattavat-tyot
                                            :rahavaraus-lupaukseen-1
                                            :hoidonjohtopalkkio
                                            :toimistokulut
                                            :erillishankinnat}
        kiinteahintaisten-toimenpideinstanssit (flatten
                                                  (q (str "WITH toimenpide_idt AS (
                                                             SELECT id
                                                             FROM toimenpidekoodi
                                                             WHERE taso = 3 AND
                                                                   koodi IN ('20107', '20191', '23104', '23116', '23124', '14301')
                                                           )
                                                           SELECT id
                                                           FROM toimenpideinstanssi
                                                           WHERE urakka = " urakka-id " AND
                                                                 toimenpide IN (SELECT id FROM toimenpide_idt);")))
        mhu-johto-toimenpideinstanssi (ffirst (q (str "SELECT id
                                                       FROM toimenpideinstanssi
                                                       WHERE urakka = " urakka-id " AND
                                                             toimenpide = (SELECT id FROM toimenpidekoodi WHERE koodi = '23151');")))]
    (testing "Kiinteahintaisen työn tallentaminen merkkaa kustannussuunnitelman likaiseksi"
      (let [vastaukset (doall (for [tallennettava-data kiinteahintaiset-data]
                                (bs/tallenna-kiinteahintaiset-tyot (:db jarjestelma) +kayttaja-jvh+ tallennettava-data)))
            kustannussuunnitelmat (filter (fn [{:keys [tyyppi likainen]}]
                                            ;; Tällainen filteröinti tässä SQL:n WHERE sijasta, jotta saadaan mahdollisia
                                            ;; bugeja kiinni, jossa on merkattu muitakin kuin kokonaishintaisia likaisiksi tjv.
                                            (or (= "kokonaishintainen" tyyppi)
                                                (not (nil? likainen))))
                                          (q-map (str "SELECT ks.likainen, m.tyyppi
                                                       FROM kustannussuunnitelma ks
                                                         JOIN maksuera m ON m.numero = ks.maksuera
                                                         JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                                                       WHERE tpi.id IN (" (apply str (interpose ", " kiinteahintaisten-toimenpideinstanssit)) ");")))]
        (is (every? :onnistui? vastaukset) "Kiinteähintaisen työn tallentaminen epäonnistui")
        (is (every? #(= "kokonaishintainen" (:tyyppi %)) kustannussuunnitelmat) "Kiinteähintaisten maksuerän tyyppi pitäisi olla 'kokonaishintainen'")
        (is (= (count kiinteahintaiset-data) (count kustannussuunnitelmat)))
        (is (every? :likainen kustannussuunnitelmat))))
    (testing "Johto- ja hallintokorvausten tallentaminen merkkaa kustannussuunnitelman likaiseksi"
      (let [vastaus (bs/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ (first jhk-data))
            kustannussuunnitelma (filter (fn [{:keys [tyyppi likainen]}]
                                           ;; Tällainen filteröinti tässä SQL:n WHERE sijasta, jotta saadaan mahdollisia
                                           ;; bugeja kiinni, jossa on merkattu muitakin kuin kokonaishintaisia likaisiksi tjv.
                                           (or (= "kokonaishintainen" tyyppi)
                                               (not (nil? likainen))))
                                         (q-map (str "SELECT ks.*, m.tyyppi
                                                     FROM kustannussuunnitelma ks
                                                       JOIN maksuera m ON m.numero = ks.maksuera
                                                       JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                                                     WHERE tpi.id = " mhu-johto-toimenpideinstanssi ";")))]
        (is (= 1 (count kustannussuunnitelma)))
        (is (:onnistui? vastaus) "Johto- ja hallintokorvauksien tallentaminen epäonnistui")
        (is (= "kokonaishintainen" (:tyyppi (first kustannussuunnitelma))) "Johto- ja hallintokorvauksen maksuerän tyypin pitäisi olla 'kokonaishintainen'")
        (is (:likainen (first kustannussuunnitelma)) "Johto- ja hallintokorvauksen tallentamien ei merkkaa kustannussuunnitelmaa likaiseksi")))
    (testing "Sampokomponentin päivittäinen työ lähettää likaiseksimerkatun kustannuksen Sampoon (kiintahintainen sekä johto- ja hallintokorvaus)"
      (vienti/aja-paivittainen-lahetys (:sonja jarjestelma) (:integraatioloki jarjestelma) (:db jarjestelma) (get-in asetukset [:sampo :lahetysjono-ulos]))
      (let [sonja-broker-tila (fn [jonon-nimi attribuutti]
                                (-> (s-tk/sonja-jolokia-jono jonon-nimi attribuutti nil) :body (cheshire/decode) (get "value")))
            viestit-jonossa (- (sonja-broker-tila (get-in asetukset [:sampo :lahetysjono-ulos])
                                                  :enqueue-count)
                               (sonja-broker-tila (get-in asetukset [:sampo :lahetysjono-ulos])
                                                  :dequeue-count))]
        (is (= (+ (* 2 (count kiinteahintaisten-toimenpideinstanssit)) ;; Jos kiinteähintainen kustannuserä on likainen, myös vastaava maksuerä on likainen.
                  1 ;; mhu-johto toimenpideinstanssi
                  )
               viestit-jonossa)
            "Sampoon ei siirry kaikki likaisiksi merkatut kiinteättyöt sekä johto- ja hallintokorvaustyöt")))
    (testing "Kustannusarvoidun työn tallentaminen merkkaa kustannussuunnitelman likaiseksi"
      (let [vastaukset (doall (for [tallennettava-data kustannusarvioitu-data]
                                (bs/tallenna-kustannusarvioitu-tyo (:db jarjestelma) +kayttaja-jvh+ tallennettava-data)))
            kustannussuunnitelmat (q-map (str "SELECT ks.likainen, m.tyyppi, tpi.*
                                               FROM kustannussuunnitelma ks
                                                 JOIN maksuera m ON m.numero = ks.maksuera
                                                 JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                                               WHERE tpi.id IN (" (apply str (interpose ", " (conj kiinteahintaisten-toimenpideinstanssit mhu-johto-toimenpideinstanssi))) ") AND
                                                     ks.likainen IS TRUE;"))]
        (println "KUSTANNUSSUUNNITELMAT " kustannussuunnitelmat)
        (is (every? :onnistui? vastaukset) "Kiinteähintaisen työn tallentaminen epäonnistui")
        (is (= 7 (count kustannussuunnitelmat))) ;; MHU-urakoissa on seitsemän toimenpideinstainssia ja niillä kullakin yksi kustannussuunnitelma
        (is (every? :likainen kustannussuunnitelmat))))
    (testing "Sampokomponentin päivittäinen työ lähettää likaiseksimerkatun kustannuksen Sampoon (kustannusarvioitu)"
      (s-tk/sonja-jolokia-jono (get-in asetukset [:sampo :lahetysjono-ulos]) nil :purge)
      (vienti/aja-paivittainen-lahetys (:sonja jarjestelma) (:integraatioloki jarjestelma) (:db jarjestelma) (get-in asetukset [:sampo :lahetysjono-ulos]))
      (let [sonja-broker-tila (fn [jonon-nimi attribuutti]
                                (-> (s-tk/sonja-jolokia-jono jonon-nimi attribuutti nil) :body (cheshire/decode) (get "value")))
            viestit-jonossa (- (sonja-broker-tila (get-in asetukset [:sampo :lahetysjono-ulos])
                                                  :enqueue-count)
                               (sonja-broker-tila (get-in asetukset [:sampo :lahetysjono-ulos])
                                                  :dequeue-count))]
        (is (= 7 viestit-jonossa)
            "Sampoon ei siirry kaikki likaisiksi merkatut kustannusarvioidut työt")))))
