(ns harja.palvelin.integraatiot.api.tyokoneenseuranta-test
  (:require [harja.palvelin.integraatiot.api.tyokoneenseuranta :refer :all :as tyokoneenseuranta]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [clojure.test :refer :all]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clojure.data.json :as json]))

(def kayttaja "carement")

(def jarjestelma-fixture (laajenna-integraatiojarjestelmafixturea kayttaja
                           :api-tyokoneenseuranta (component/using
                                                    (tyokoneenseuranta/->Tyokoneenseuranta)
                                                    [:http-palvelin :db :integraatioloki])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                      jarjestelma-fixture))


(def skeeman-tehtavat
  #{"asfaltointi",
    "auraus ja sohjonpoisto",
    "aurausviitoitus ja kinostimet",
    "harjaus",
    "huoltokierros",
    "jyrays",
    "kelintarkastus",
    "koneellinen niitto",
    "koneellinen vesakonraivaus",
    "kuumennus",
    "l- ja p-alueiden puhdistus",
    "liikennemerkkien puhdistus",
    "liik. opast. ja ohjausl. hoito seka reunapaalujen kun.pito",
    "liikenteen varmistaminen kelirikkokohteessa",
    "linjahiekoitus",
    "lumensiirto",
    "lumivallien madaltaminen",
    "muu",
    "muut valaistusurakoiden toimenpiteet",
    "ojitus",
    "paallysteiden juotostyot",
    "paallysteiden paikkaus",
    "paannejaan poisto",
    "palteen poisto",
    "palteen poisto kaiteen alta",
    "pinnan tasaus",
    "pistehiekoitus",
    "paallystetyn tien polynsidonta",
    "paallystetyn tien sorapientareen taytto",
    "reunapaalujen uusiminen",
    "roskien keruu",
    "ryhmavaihto",
    "sekoitus tai stabilointi",
    "siltojen puhdistus",
    "sohjo-ojien teko",
    "sorastus",
    "sorapientareen taytto",
    "sorateiden muokkaushoylays",
    "sorateiden polynsidonta",
    "sorateiden tasaus",
    "sulamisveden haittojen torjunta",
    "suolaus",
    "tiemerkinta",
    "tiestotarkastus",
    "tilaajan laadunvalvonta",
    "turvalaite"})

(deftest tallenna-tyokoneen-seurantakirjaus-uusi
  (let [_ (anna-kirjoitusoikeus kayttaja)
        kutsu (api-tyokalut/post-kutsu
                ;; kokonaan uusi tyokone, kantaan pitäisi tulla uusi rivi
                ["/api/seuranta/tyokone"] kayttaja portti (-> "test/resurssit/api/tyokoneseuranta_testi.json"
                                                            slurp
                                                            (.replace "__TEHTAVA__" "suolaus")))]
    (let [[sijainti] (first (q "SELECT st_astext(sijainti) FROM tyokonehavainto WHERE tyokoneid=666"))
          tehtavat (-> (ffirst (q "SELECT tehtavat FROM tyokonehavainto WHERE tyokoneid=666"))
                     (konv/array->set))]
      (is (= 200 (:status kutsu)))
      (is (= (str sijainti) "POINT(429015 7198161)"))
      (is (= tehtavat #{"suolaus"})))))

(deftest tallenna-tyokoneen-seurantakirjaus-olemassaoleva
  (let [_ (anna-kirjoitusoikeus kayttaja)
        kutsu (api-tyokalut/post-kutsu
                ;; tyokone 31337 on jo kannassa, katsotaan tuleeko uusi rivi
                ["/api/seuranta/tyokone"] kayttaja portti (slurp "test/resurssit/api/tyokoneseuranta.json"))]
    (let [rivit
          (mapv first
            (q "SELECT st_astext(sijainti::GEOMETRY) FROM tyokonehavainto WHERE tyokoneid=31337 ORDER BY vastaanotettu ASC"))]

      (is (= 200 (:status kutsu)))
      (is (= 2 (count rivit)))
      (is (= (str (second rivit)) "POINT(429005 7198151)")))))

(deftest tallenna-tyokoneen-seurantakirjaus-viivageometrialla
  (let [_ (anna-kirjoitusoikeus kayttaja)
        kutsu (api-tyokalut/post-kutsu
                ["/api/seuranta/tyokone/reitti"] kayttaja portti (slurp "test/resurssit/api/tyokoneenseurannan-kirjaus-viivageometrialla-testi.json"))
        sijainti (first (q "SELECT  st_asgeojson(sijainti) FROM tyokonehavainto WHERE tyokoneid=999"))
        tehtavat (-> (ffirst (q "SELECT tehtavat FROM tyokonehavainto WHERE tyokoneid = 999 ORDER BY tehtavat"))
                   (konv/array->set))]
    (is (= 200 (:status kutsu)))
    (is (= (json/read-str (first sijainti))
          {"type" "LineString",
           "coordinates" [[498919 7247099] [499271 7248395] [499399 7249019] [499820 7249885] [498519 7247299] [499371 7248595] [499499 7249319] [499520 7249685]]}))
    (is (= tehtavat #{"auraus ja sohjonpoisto" "suolaus"}))))
(deftest tallenna-tyokoneen-seurantakirjaus-suunta-null
  ;; Suunta ei ole pakollinen. Paikallaan oleva työkone saa lähettää seurantakirjauksen jossa suunta on null.
  (let [_ (anna-kirjoitusoikeus kayttaja)
        kutsu (api-tyokalut/post-kutsu
                ["/api/seuranta/tyokone"] kayttaja portti (-> "test/resurssit/api/tyokoneseuranta_testi.json"
                                                            slurp
                                                            (.replace "\"suunta\": 47" "\"suunta\": null")
                                                            (.replace "__TEHTAVA__" "ojitus")))]
    (let [suunta-kannassa (ffirst (q "SELECT suunta FROM tyokonehavainto WHERE tyokoneid=666 ORDER BY vastaanotettu DESC LIMIT 1"))]
      (is (= 200 (:status kutsu)))
      (is (= suunta-kannassa nil)
        (str "Suunta '" suunta-kannassa "' raportoitu onnistuneesti")))))
(deftest tallenna-tyokoneen-seurantakirjaus-suunta-puuttuu
  ;; Suunta ei ole pakollinen. Paikallaan oleva työkone saa lähettää seurantakirjauksen ilman suuntaa.
  (let [_ (anna-kirjoitusoikeus kayttaja)
        kutsu (api-tyokalut/post-kutsu
                ["/api/seuranta/tyokone"] kayttaja portti (-> "test/resurssit/api/tyokoneseuranta_testi.json"
                                                            slurp
                                                            (.replace "\"suunta\": 47," "")
                                                            (.replace "__TEHTAVA__" "ojitus")))]
    (let [suunta-kannassa (ffirst (q "SELECT suunta FROM tyokonehavainto WHERE tyokoneid=666 ORDER BY vastaanotettu DESC LIMIT 1"))]
      (is (= 200 (:status kutsu)))
      (is (= suunta-kannassa nil)
        (str "Suunta '" suunta-kannassa "' raportoitu onnistuneesti")))))
(deftest kaikkien-tehtavien-kirjaus-toimii
  (doseq [tehtava skeeman-tehtavat]
    (let [_ (anna-kirjoitusoikeus kayttaja)
          kutsu (api-tyokalut/post-kutsu
                  ["/api/seuranta/tyokone"] kayttaja portti (-> "test/resurssit/api/tyokoneseuranta_testi.json"
                                                              slurp
                                                              (.replace "__TEHTAVA__" tehtava)))]
      (let [tehtavat-kannassa (-> (ffirst (q "SELECT tehtavat FROM tyokonehavainto WHERE tyokoneid=666 ORDER BY vastaanotettu DESC LIMIT 1"))
                                (konv/array->set))
            tehtava-kannassa (first tehtavat-kannassa)]
        (is (= 200 (:status kutsu)))
        (is (= tehtava-kannassa tehtava)
          (str "Tehtävä '" tehtava "' raportoitu onnistuneesti"))))))

