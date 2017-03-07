(ns harja.views.urakka.yllapitokohteet.muut-kustannukset-test
  (:require
   [cljs-time.core :as t]
   [cljs.test :as test :refer-macros [deftest is async]]
   [harja.loki :refer [log tarkkaile!]]
   [harja.atom :refer [paivita!]]
   [harja.ui.historia :as historia]
   [harja.domain.paallystysilmoitus :as pot]
   [harja.domain.tierekisteri :as tierekisteri-domain]
   [harja.ui.tierekisteri :as tierekisteri]
   [harja.testutils.shared-testutils :refer [render paivita sel sel1 grid-solu click change
                                             disabled? ilman-tavutusta komponentti-fixture
                                             text change]]
   [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu
                            jvh-fixture]]
   [harja.views.urakka.paallystysilmoitukset :as p]
   [harja.pvm :as pvm]
   [reagent.core :as r]
   [cljs.core.async :refer [<! >!]]
   [cljs-react-test.simulate :as sim]
   [schema.core :as s]
   [harja.tiedot.urakka.yllapitokohteet.muut-kustannukset :as tiedot]
   [harja.views.urakka.yllapitokohteet.muut-kustannukset :as view]
   [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.testutils.macros :refer [komponenttitesti]]))

(test/use-fixtures :each komponentti-fixture fake-palvelut-fixture jvh-fixture)

#_(def feikki-grid-tiedot
  [{:id "ypt-5",
    :urakka 5,
    :selite "aaaa",
    :pvm #inst "2017-03-01T00:00:00",
    :hinta 234,
    :yllapitoluokka
    {:nimi "Ei ylläpitoluokkaa", :lyhyt-nimi "-", :numero nil},
    :laskentakohde [nil nil],
    :muokattava true}
   {:hinta -1500,
    :pvm #inst "2017-01-02T00:06:06",
    :selite "Ylläpidon sakko",
    :id "sanktio-21",
    :muokattava false}
   {:hinta 2000,
    :pvm #inst "2017-01-03T00:12:06",
    :selite "Ylläpidon bonus",
    :id "sanktio-23",
    :muokattava false}
   {:hinta 0,
    :pvm #inst "2017-01-12T00:16:06",
    :selite "Ylläpidon muistutus",
    :id "sanktio-25",
    :muokattava false}
   ])

(def feikit-urakan-sanktiot (list {:suorasanktio true,
                                                       :laji :yllapidon_sakko,
                                                       :laatupoikkeama {:aika #inst "2017-01-02T00:06:06"},
                                                       :summa 1500,
                                                       :indeksi nil,
                                                       :toimenpideinstanssi 15,
                                                       :id 21,
                                                       :perintapvm #inst "2017-01-04T00:00:00",
                                                       :tyyppi {:id 4, :toimenpidekoodi nil, :nimi "Ylläpidon sakko"},
                                                       :vakiofraasi nil}
                                                      {:suorasanktio true,
                                                       :laji :yllapidon_sakko,
                                                       :laatupoikkeama {:aika #inst "2017-01-03T00:12:06"},
                                                       :summa 1500,
                                                       :indeksi nil,
                                                       :toimenpideinstanssi 15,
                                                       :id 22,
                                                       :perintapvm #inst "2017-01-05T00:00:00",
                                                       :tyyppi {:id 4, :toimenpidekoodi nil, :nimi "Ylläpidon sakko"},
                                                       :vakiofraasi nil}
                                                      {:suorasanktio true,
                                                       :laji :yllapidon_bonus,
                                                       :laatupoikkeama {:aika #inst "2017-01-03T00:12:06"},
                                                       :summa -2000,
                                                       :indeksi nil,
                                                       :toimenpideinstanssi 15,
                                                       :id 23,
                                                       :perintapvm #inst "2017-01-06T00:00:00",
                                                       :tyyppi {:id 5, :toimenpidekoodi nil, :nimi "Ylläpidon bonus"},
                                                       :vakiofraasi nil}
                                                      {:suorasanktio true,
                                                       :laji :yllapidon_muistutus,
                                                       :laatupoikkeama {:aika #inst "2017-01-12T00:16:06"},
                                                       :summa nil,
                                                       :indeksi nil,
                                                       :toimenpideinstanssi 15,
                                                       :id 24,
                                                       :perintapvm #inst "2017-01-15T00:00:00",
                                                       :tyyppi {:id 6, :toimenpidekoodi nil, :nimi "Ylläpidon muistutus"},
                                                       :vakiofraasi nil}
                                                      {:suorasanktio true,
                                                       :laji :yllapidon_muistutus,
                                                       :laatupoikkeama {:aika #inst "2017-01-12T00:16:06"},
                                                       :summa nil,
                                                       :indeksi nil,
                                                       :toimenpideinstanssi 15,
                                                       :id 25,
                                                       :perintapvm #inst "2017-01-16T00:00:00",
                                                       :tyyppi {:id 6, :toimenpidekoodi nil, :nimi "Ylläpidon muistutus"},
                                                       :vakiofraasi nil}
                                                      {:suorasanktio true,
                                                       :laji :yllapidon_sakko,
                                                       :laatupoikkeama {:aika #inst "2017-01-02T00:06:06"},
                                                       :summa 1500,
                                                       :indeksi nil,
                                                       :toimenpideinstanssi 15,
                                                       :id 26,
                                                       :perintapvm #inst "2017-01-04T00:00:00",
                                                       :tyyppi {:id 4, :toimenpidekoodi nil, :nimi "Ylläpidon sakko"},
                                                       :vakiofraasi nil}))

#_(deftest muut-kustannukset-taulukko
  (let [
        hae-toteumat-kanava (fake-palvelukutsu :hae-yllapito-toteumat [{:id 5, :urakka 5, :selite "aaaa", :pvm #inst "2017-03-01T00:00:00", :hinta 234, :yllapitoluokka {:nimi "Ei ylläpitoluokkaa", :lyhyt-nimi "-", :numero nil}, :laskentakohde [nil nil]}])
        tallenna-toteuma-kanava (fake-palvelukutsu :tallenna-yllapito-toteuma {:toteumat [], :laskentakohteet []})
        hae-sanktiot-kanava (fake-palvelukutsu :hae-urakan-sanktiot feikit-urakan-sanktiot)
        urakka {:id 5}
        komponentti (fn []
                      [:div "terve"])]
    ;; (paivita! tiedot/kohdistamattomien-sanktioiden-tiedot)
    ;; (paivita! tiedot/muiden-kustannusten-tiedot)
    (log "ennen async")
    (async done
           (log "ennen go")
           (go
             (log "ennen render")
             (render [komponentti])
             (log "ennen paivita")
             (paivita)
             (log "nappi:" (pr-str  (sel1 ".grid-tallenna")))
             ;; (log "doc lastChild" (.-lastChild js/document.body ))
             (click ".grid-tallenna")
             (let [tt (<! tallenna-toteuma-kanava)]
               (log "toteuma tallennettu. saatiin" (pr-str tt))
               (is (some? tt))))
           (done))))

(def urakka
  {:id 4 :nimi "Oulun urakka"
   :urakoitsija {:nimi "YIT Rakennus Oyj" :id 2}
   :hallintayksikko {:nimi "Pohjois-Pohjanmaa" :id 9}})

(defn aseta-solu [rivi-nr sarake-nr arvo]
  (sim/change (grid-solu "muut-kustannukset-grid" rivi-nr sarake-nr) {:target {:value arvo}}))

(deftest muut-kustannukset-komponenttitesti
  (komponenttitesti [view/muut-kustannukset urakka]
                    (is (sel1 ".muut-kustannukset") "Löytyi taulukon div")
                    (is (= (text (sel1 "button")) "Muokkaa") "Muokkaa-nappi löytyy")
                    (click (sel1 "button"))
                    --
                    (log "napit" (pr-str  (map text (sel "button"))))
                    (is (sel1 ".grid-tallenna") "Löytyi tallenna-nappi")
                    (is (sel1 ".grid-lisaa") "Löytyi lisää-nappi")
                    (click (sel1 ".grid-lisaa"))
                    (sim/change (grid-solu "muut-kustannukset-grid" 0 0) {:target {:value "12.12.2017"}})
                    --
                    (is (-> ".grid-tallenna" sel1 disabled?) "tallenna vielä disabled")
                    (log "solun arvo" (pr-str (text (grid-solu "muut-kustannukset-grid" 0 1))))
                    (change (grid-solu "muut-kustannukset-grid" 0 1) "fff")
                    --
                    (log "solun arvo" (pr-str (text (grid-solu "muut-kustannukset-grid" 0 1))))
                    ))
