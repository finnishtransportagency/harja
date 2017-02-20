(ns harja.views.urakka.valitavoitteet-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.views.urakka.valitavoitteet :as valitavoitteet]
    [harja.loki :refer [log]]
    [harja.testutils.shared-testutils :as u]
    [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu jvh-fixture]]
    [harja.tyokalut.functor :refer [fmap]])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(test/use-fixtures :each u/komponentti-fixture fake-palvelut-fixture jvh-fixture)

(def testidata [{:valmis-merkitsija nil, :valmispvm nil, :valtakunnallinen-id 666,
                 :urakka-id 349, :luotu (t/now),
                 :valtakunnallinen-takarajan-toistokuukausi 10, :sakko nil,
                 :valmis-merkitsija-etunimi nil, :takaraja (t/plus (t/now) (t/hours 4)),
                 :luoja 26, :valmis-kommentti nil, :valtakunnallinen-takaraja nil,
                 :nimi "Pienmerkinnät raportoitu tilaajalle", :muokkaaja nil, :id 1196,
                 :valmis-merkitsija-sukunimi nil, :valtakunnallinen-nimi "Pienmerkinnät raportoitu tilaajalle",
                 :valmis-merkitty nil, :muokattu nil, :valtakunnallinen-takarajan-toistopaiva 15,
                 :viikkosakko nil}
                {:valmis-merkitsija nil, :valmispvm nil, :valtakunnallinen-id nil,
                 :urakka-id 349, :luotu (t/now),
                 :valtakunnallinen-takarajan-toistokuukausi nil, :sakko nil,
                 :valmis-merkitsija-etunimi nil, :takaraja nil, :luoja 20,
                 :valmis-kommentti nil, :valtakunnallinen-takaraja nil, :nimi nil,
                 :muokkaaja nil, :id 21, :valmis-merkitsija-sukunimi nil, :valtakunnallinen-nimi nil,
                 :valmis-merkitty nil, :muokattu nil, :valtakunnallinen-takarajan-toistopaiva nil,
                 :viikkosakko nil}])

(deftest urakan-omat-valitavoitteet-toimii
  (let [urakka {:id 4}
        kaikki-valitavoitteet-atom
        (atom testidata)
        urakan-valitavoitteet (filterv (comp not :valtakunnallinen-id) @kaikki-valitavoitteet-atom)]
    (komponenttitesti
      [valitavoitteet/urakan-omat-valitavoitteet
       {:urakka urakka
        :kaikki-valitavoitteet-atom kaikki-valitavoitteet-atom
        :urakan-valitavoitteet urakan-valitavoitteet
        :valittu-urakan-vuosi 2017}]

      (is (u/sel1 [:table.grid]) "Komponentin mounttaus toimii"))))

(deftest urakan-omat-valitavoitteet-toimii-kun-valintana-kaikki-vuodet
  (let [urakka {:id 4}
        kaikki-valitavoitteet-atom
        (atom testidata)
        urakan-valitavoitteet (filterv (comp not :valtakunnallinen-id) @kaikki-valitavoitteet-atom)]
    (komponenttitesti
      [valitavoitteet/urakan-omat-valitavoitteet
       {:urakka urakka
        :kaikki-valitavoitteet-atom kaikki-valitavoitteet-atom
        :urakan-valitavoitteet urakan-valitavoitteet
        :valittu-urakan-vuosi :kaikki}]

      (is (u/sel1 [:table.grid]) "Komponentin mounttaus toimii"))))

(deftest urakan-omat-ja-valtakunnalliset-valitavoitteet-toimii
  (let [urakka {:id 4}
        kaikki-valitavoitteet-atom
        (atom testidata)]

    (komponenttitesti
      [valitavoitteet/urakan-omat-ja-valtakunnalliset-valitavoitteet
       {:urakka urakka
        :kaikki-valitavoitteet-atom kaikki-valitavoitteet-atom
        :valittu-urakan-vuosi 2017}]

      (is (u/sel1 [:table.grid]) "Komponentin mounttaus toimii"))))

(deftest valtakunnalliset-valitavoitteet-toimii
  (let [urakka {:id 4}
        kaikki-valitavoitteet-atom
        (atom testidata)
        valtakunnalliset-valitavoitteet (filterv :valtakunnallinen-id @kaikki-valitavoitteet-atom)]

    (komponenttitesti
      [valitavoitteet/valtakunnalliset-valitavoitteet
       {:urakka urakka
        :kaikki-valitavoitteet-atom kaikki-valitavoitteet-atom
        :valtakunnalliset-valitavoitteet valtakunnalliset-valitavoitteet
        :valittu-urakan-vuosi 2017}]

      (is (u/sel1 [:table.grid]) "Komponentin mounttaus toimii"))))