(ns harja.views.urakka.yllapitokohteet.muut-kustannukset-test
  (:require
   [cljs-time.core :as t]
   [cljs.test :as test :refer-macros [deftest is async]]
   [harja.loki :refer [log tarkkaile!]]
   [harja.ui.historia :as historia]
   [harja.domain.paallystysilmoitus :as pot]
   [harja.domain.tierekisteri :as tierekisteri-domain]
   [harja.ui.tierekisteri :as tierekisteri]
   [harja.testutils.shared-testutils :refer [render paivita sel sel1 grid-solu click change
                                             disabled? ilman-tavutusta komponentti-fixture]]
   [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu
                            jvh-fixture]]
   [harja.views.urakka.paallystysilmoitukset :as p]
   [harja.pvm :as pvm]
   [reagent.core :as r]
   [cljs.core.async :refer [<! >!]]
   [cljs-react-test.simulate :as sim]
   [schema.core :as s]
   [harja.tiedot.urakka.muut-kustannukset :as tiedot-muut-kustannukset]
   [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(test/use-fixtures :each komponentti-fixture fake-palvelut-fixture jvh-fixture)

(def feikki-grid-tiedot
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



(deftest muut-kustannukset-taulukko
  (fake-palvelukutsu :hae-yllapito-toteumat [{:id 5, :urakka 5, :selite "aaaa", :pvm #object[Object 20170301T000000], :hinta 234, :yllapitoluokka {:nimi "Ei ylläpitoluokkaa", :lyhyt-nimi "-", :numero nil}, :laskentakohde [nil nil]}])
  (fake-palvelukutsu :hae-urakan-sanktiot (list {:suorasanktio true,
                                                 :laji :yllapidon_sakko,
                                                 :laatupoikkeama {:aika #object[Object 20170102T000606]},
                                                 :summa 1500,
                                                 :indeksi nil,
                                                 :toimenpideinstanssi 15,
                                                 :id 21,
                                                 :perintapvm #object[Object 20170104T000000],
                                                 :tyyppi {:id 4, :toimenpidekoodi nil, :nimi "Ylläpidon sakko"},
                                                 :vakiofraasi nil}
                                                {:suorasanktio true,
                                                 :laji :yllapidon_sakko,
                                                 :laatupoikkeama {:aika #object[Object 20170103T001206]},
                                                 :summa 1500,
                                                 :indeksi nil,
                                                 :toimenpideinstanssi 15,
                                                 :id 22,
                                                 :perintapvm #object[Object 20170105T000000],
                                                 :tyyppi {:id 4, :toimenpidekoodi nil, :nimi "Ylläpidon sakko"},
                                                 :vakiofraasi nil}
                                                {:suorasanktio true,
                                                 :laji :yllapidon_bonus,
                                                 :laatupoikkeama {:aika #object[Object 20170103T001206]},
                                                 :summa -2000,
                                                 :indeksi nil,
                                                 :toimenpideinstanssi 15,
                                                 :id 23,
                                                 :perintapvm #object[Object 20170106T000000],
                                                 :tyyppi {:id 5, :toimenpidekoodi nil, :nimi "Ylläpidon bonus"},
                                                 :vakiofraasi nil}
                                                {:suorasanktio true,
                                                 :laji :yllapidon_muistutus,
                                                 :laatupoikkeama {:aika #object[Object 20170112T001606]},
                                                 :summa nil,
                                                 :indeksi nil,
                                                 :toimenpideinstanssi 15,
                                                 :id 24,
                                                 :perintapvm #object[Object 20170115T000000],
                                                 :tyyppi {:id 6, :toimenpidekoodi nil, :nimi "Ylläpidon muistutus"},
                                                 :vakiofraasi nil}
                                                {:suorasanktio true,
                                                 :laji :yllapidon_muistutus,
                                                 :laatupoikkeama {:aika #object[Object 20170112T001606]},
                                                 :summa nil,
                                                 :indeksi nil,
                                                 :toimenpideinstanssi 15,
                                                 :id 25,
                                                 :perintapvm #object[Object 20170116T000000],
                                                 :tyyppi {:id 6, :toimenpidekoodi nil, :nimi "Ylläpidon muistutus"},
                                                 :vakiofraasi nil}
                                                {:suorasanktio true,
                                                 :laji :yllapidon_sakko,
                                                 :laatupoikkeama {:aika #object[Object 20170102T000606]},
                                                 :summa 1500,
                                                 :indeksi nil,
                                                 :toimenpideinstanssi 15,
                                                 :id 26,
                                                 :perintapvm #object[Object 20170104T000000],
                                                 :tyyppi {:id 4, :toimenpidekoodi nil, :nimi "Ylläpidon sakko"},
                                                 :vakiofraasi nil})
                     ))
