(ns harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset-test
  "Pyrkii testaamaan lupausmuistutustehtävän toimintaa.
  Ei testata: ajastus, FIM ja varsinainen sähköpostin lähetys."
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]
            [harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset :as lupausmuistutukset]
            [harja.palvelin.palvelut.lupaus.lupaus-muistutus :as lupaus-muistutus]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.integraatiot.sahkoposti :refer [Sahkoposti]]
            [clojure.string :as str]
            [harja.kyselyt.lukot :as qk]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(defn- etsi-urakka [urakat nimi]
  (->>
    urakat
    (filter #(= (:nimi %) nimi))
    first))

(defn- urakat->oulun-mhu [urakat]
  (etsi-urakka urakat "Oulun MHU 2019-2024"))

(defn- urakat->iin-mhu [urakat]
  (etsi-urakka urakat "Iin MHU 2021-2026"))

(deftest hae-muistutettavat-urakat-toimii-2019-alkavalle-urakalle
  (testing "Päivää ennen urakan alkamista"
    (let [nyt (pvm/->pvm "30.09.2019")
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (empty? urakat)) "Ei muistuteta ennnen urakan alkamista (1.10.)"))

  (testing "Urakan ensimmäisenä päivänä"
    (let [nyt (pvm/->pvm "01.10.2019")
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (urakat->oulun-mhu urakat) "Oulun MHU on muistutettava 2019-alkuinen urakka")
      (is (not (urakat->iin-mhu urakat)) "Iin MHU ei ole 2019-alkuinen urakka")))

  (testing "Urakan viimeisenä päivänä"
    (let [nyt (pvm/->pvm "30.09.2024")
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (urakat->oulun-mhu urakat) "Oulun MHU on muistutettava 2019-alkuinen urakka")
      (is (not (urakat->iin-mhu urakat)) "Iin MHU ei ole 2019-alkuinen urakka")))

  (testing "Yksi päivä urakan päättymisen jälkeen"
    (let [nyt (pvm/->pvm "01.10.2024")
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (urakat->oulun-mhu urakat) "Oulun MHU on muistutettava 2019-alkuinen urakka")
      (is (not (urakat->iin-mhu urakat)) "Iin MHU ei ole 2019-alkuinen urakka")))

  (testing "Kolme kuukautta urakan päättymisen jälkeen"
    (let [nyt (pvm/->pvm "01.01.2025")
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (empty? urakat) "Ei muistuteta kolme kuukautta urakan päättymisen jälkeen"))))

(deftest hae-muistutettavat-urakat-toimii-2021-alkavalle-urakalle
  (let [nyt (pvm/->pvm "01.10.2021")
        urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2021)]
    (is (urakat->iin-mhu urakat) "Iin MHU on muistutettava 2021-alkuinen urakka")
    (is (not (urakat->oulun-mhu urakat)) "Oulun MHU ei ole 2021-alkuinen urakka")))

(def iin-vastaanottaja "ii@testi.test")
(def oulun-vastaanottaja "oulu@testi.test")

(def lahetetyt
  "FakeSahkoposti-rajapinnan kautta lähetetyt viestit"
  (atom []))

(defn vastaanottajat [viestit]
  (into
    #{}
    (map :vastaanottaja)
    viestit))

;; Tallentaa rajapintaan lähetetyt sähköpostiviestit, mutta ei lähetä oikeasti mitään
(defrecord FakeSahkoposti []
  Sahkoposti
  (laheta-viesti! [_this _lahettaja vastaanottaja otsikko sisalto]
    (println
      "Lähetetään leikisti sähköposti"
      (pr-str {:vastaanottaja vastaanottaja :otsikko otsikko :sisalto sisalto}))
    (assert (not (str/blank? vastaanottaja)))
    (assert (not (str/blank? otsikko)))
    (assert (not (str/blank? sisalto)))
    (swap! lahetetyt conj {:vastaanottaja vastaanottaja :otsikko otsikko :sisalto sisalto}))
  (vastausosoite [_this]
    "vastausosoite@foo.bar"))

(deftest muistutustehtava
  (reset! lahetetyt [])
  (let [db (:db jarjestelma)
        fim {}
        sahkoposti (->FakeSahkoposti)]
    (with-redefs [lupaus-muistutus/urakan-vastaanottajat (fn [_fim sampoid]
                                                           (case sampoid
                                                             "1242141-II3" [iin-vastaanottaja]
                                                             "1242141-OULU3" [oulun-vastaanottaja]
                                                             []))]
      (lupausmuistutukset/muistutustehtava db fim sahkoposti (pvm/->pvm "01.10.2019"))
      (is
        (empty? @lahetetyt)
        "1.10.2019 ei pitäisi lähteä vielä sähköposteja, koska lokakuun lupauksiin vastataan vasta marraskuussa")
      (reset! lahetetyt [])

      (lupausmuistutukset/muistutustehtava db fim sahkoposti (pvm/->pvm "01.11.2019"))
      (is
        (empty? @lahetetyt)
        "Oulun urakalle pitäisi lähteä sähköposti vasta kun luvatut pisteet on tallennettu")
      (reset! lahetetyt [])

      (is
        (lupaus-palvelu/tallenna-urakan-luvatut-pisteet db +kayttaja-jvh+
          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
           :pisteet 77})
        "Luvattujen pisteiden tallennus pitäisi onnistua")

      ;; avataan lukko käsin lähetysten välissä, koska todellisuudessa tapahtumien välillä pitkiä aikoja, ja
      ;; uuden lukkomekaniikan myötä tällaisessa setupissa lukko jää nyt muka päälle, vaikka on oikeasti vain 2min päällä
      (qk/avaa-lukko? db "lupaus-muistutukset")
      (lupausmuistutukset/muistutustehtava db fim sahkoposti (pvm/->pvm "01.11.2019"))
      (is
        (= #{oulun-vastaanottaja} (vastaanottajat @lahetetyt))
        "1.11.2019 pitäisi lähteä sähköposti Oulun urakalle")
      (reset! lahetetyt [])

      (qk/avaa-lukko? db "lupaus-muistutukset")
      (lupausmuistutukset/muistutustehtava db fim sahkoposti (pvm/->pvm "01.11.2021"))
      (is
        (= #{oulun-vastaanottaja iin-vastaanottaja} (vastaanottajat @lahetetyt))
        "1.11.2021 pitäisi lähteä sähköpostit Oulun ja Iin urakoille")
      (reset! lahetetyt [])

      (qk/avaa-lukko? db "lupaus-muistutukset")
      (lupausmuistutukset/muistutustehtava db fim sahkoposti (pvm/->pvm "1.9.2026"))
      (is
        (= #{iin-vastaanottaja} (vastaanottajat @lahetetyt))
        "1.9.2026 pitäisi lähteä sähköposti Iin urakalle (Oulun urakka on jo päättynyt)")
      (reset! lahetetyt [])

      (qk/avaa-lukko? db "lupaus-muistutukset")
      (lupausmuistutukset/muistutustehtava db fim sahkoposti (pvm/->pvm "1.10.2026"))
      (is
        (empty? @lahetetyt)
        "1.10.2026 ei lähde enää sähköposteja (urakat päättyneet)")
      (qk/avaa-lukko? db "lupaus-muistutukset"))))
