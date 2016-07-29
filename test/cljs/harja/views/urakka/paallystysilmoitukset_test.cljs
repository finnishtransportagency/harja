(ns harja.views.urakka.paallystysilmoitukset-test
  (:require
   [cljs-time.core :as t]
   [cljs.test :as test :refer-macros [deftest is async]]
   [harja.loki :refer [log tarkkaile!]]
   [harja.ui.historia :as historia]
   [harja.domain.paallystysilmoitus :as pot]
   [harja.domain.tierekisteri :as tierekisteri-domain]
   [harja.ui.tierekisteri :as tierekisteri]
   [harja.testutils :refer [komponentti-fixture fake-palvelut-fixture fake-palvelukutsu jvh-fixture
                            render paivita sel sel1 grid-solu click]]
   [harja.views.urakka.paallystysilmoitukset :as p]
   [harja.pvm :as pvm]
   [reagent.core :as r]
   [cljs.core.async :refer [<! >!]]
   [cljs-react-test.simulate :as sim]
   [schema.core :as s]
   [harja.domain.paallystysilmoitus :as pot])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(test/use-fixtures :each komponentti-fixture fake-palvelut-fixture jvh-fixture)

(deftest tien-pituus-laskettu-oikein
  (let [tie1 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 3 :tr-loppuetaisyys 5}
        tie2 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 5 :tr-loppuetaisyys 5}
        tie3 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 3 :tr-loppuetaisyys -100}
        tie4 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 1 :tr-loppuetaisyys 2}
        tie5 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 0 :tr-loppuetaisyys 1}
        tie6 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 1}]
    (is (= (tierekisteri-domain/laske-tien-pituus tie1) 2))
    (is (= (tierekisteri-domain/laske-tien-pituus tie2) 0))
    (is (= (tierekisteri-domain/laske-tien-pituus tie3) 103))
    (is (= (tierekisteri-domain/laske-tien-pituus tie4) 1))
    (is (= (tierekisteri-domain/laske-tien-pituus tie5) 1))
    (is (= (tierekisteri-domain/laske-tien-pituus tie6) nil))))

(deftest muutos-kokonaishintaan-laskettu-oikein
  (let [tyot [{:tilattu-maara 10 :toteutunut-maara 15 :yksikkohinta 1}
              {:tilattu-maara 15 :toteutunut-maara 15  :yksikkohinta 666}
              {:tilattu-maara 4 :toteutunut-maara 5 :yksikkohinta 8}]
    tyot2 [{:tilattu-maara 4 :toteutunut-maara 2 :yksikkohinta 15}]]
    (is (= (pot/laske-muutokset-kokonaishintaan tyot) 13))
    (is (= (pot/laske-muutokset-kokonaishintaan tyot2) -30))))

(def paallystysilmoituslomake-alkutila
  {:tila :aloitettu
   :muutoshinta 666
   :kohdenimi nil
   :kohdenumero "666"
   :kommentit []
   :tr-numero 20
   :tr-alkuosa 1
   :tr-alkuetaisyys 1
   :tr-loppuosa 5
   :tr-loppuetaisyys 100
   :valmispvm-kohde nil
   :sopimuksen-mukaiset-tyot nil

   :kaasuindeksi nil
   :aloituspvm nil
   :paallystyskohde-id 20
   :bitumi-indeksi nil
   :id 8
   :takuupvm nil
   :ilmoitustiedot {:tyot [{:tyo "jotain" :tyyppi :ajoradan-paallyste :yksikko "m"
                            :yksikkohinta 6.66
                            :tilattu-maara 10
                            :toteutunut-maara 110}]
                    :osoitteet [{:tr-numero 20
                                 :tr-alkuosa 3 :tr-alkuetaisyys 123
                                 :tr-loppuosa 5 :tr-loppuetaisyys 100
                                 :kohdeosa-id 31 :tr-kaista 26 :tr-ajorata 2
                                 :tunnus "k"  :massamenekki 42
                                 :nimi "piru 2" :raekoko 66
                                 :paallystetyyppi 1
                                 :toimenpide "ja kolomas"}
                                {:tr-numero 20
                                 :tr-alkuosa 1 :tr-alkuetaisyys 1
                                 :tr-loppuosa 3 :tr-loppuetaisyys 42
                                 :kohdeosa-id 30 :tr-kaista 1 :tr-ajorata 0
                                 :tunnus "p"  :nimi "piru 1"
                                 :toimenpide "eka?"}
                                {:tr-numero 20
                                 :tr-alkuosa 3 :tr-alkuetaisyys 42
                                 :tr-loppuosa 3 :tr-loppuetaisyys 123
                                 :kohdeosa-id 32 :tr-kaista nil :lisaaineet "huono tyyri"
                                 :tr-ajorata nil :sideainetyyppi 13 :muotoarvo "1"
                                 :esiintyma "kovasti kovestä" :pitoisuus 44
                                 :tunnus "y" :nimi "piru 1.5"
                                   :toimenpide "toka!"
                                 :km-arvo "9"}]
                    :alustatoimet []}
   :asiatarkastus {:lisatiedot nil :tarkastusaika nil :tarkastaja nil}
   :arvonvahennykset nil
   :tekninen-osa {:paatos nil :asiatarkastus nil :kasittelyaika nil :perustelu nil}
   :valmispvm-paallystys nil
   :taloudellinen-osa {:kasittelyaika nil :perustelu nil :paatos nil :asiatarkastus nil}
   :kokonaishinta 0})

(defn tarkista-asiatarkastus [lomake]
  (go

    (is (not (sel1 :.pot-asiatarkastus)) "Asiatarkastus ei näkyvissä vielä")

    (swap! lomake assoc
           :tila :valmis
           :valmispvm-kohde (pvm/nyt))

    (<! (paivita))

    (is (some? (sel1 :.pot-asiatarkastus)) "Asiatarkastus näkyvissä")))

(deftest paallystysilmoituslomake
  (let [urakka {:id 1}
        lukko nil
        lomake (r/atom paallystysilmoituslomake-alkutila)
        historia (historia/historia lomake)
        _ (historia/kuuntele! historia)
        comp (fn []
               [p/paallystysilmoituslomake urakka @lomake lukko (partial swap! lomake) historia])
        pituudet-haettu (fake-palvelukutsu :hae-tr-osien-pituudet (constantly {1 1000
                                                                               2 2000
                                                                               3 3000
                                                                               4 3900
                                                                               5 400}))
        _ (fake-palvelukutsu :hae-lukko-idlla (constantly :ei-lukittu))
        tallennus (fake-palvelukutsu :tallenna-paallystysilmoitus identity)]
    (async
     done
     (go
       (render [comp])

       ;; Tarkista, että tieosoite näkyy oikein
       (is (= "piru 1"
              (some-> (grid-solu "yllapitokohdeosat" 0 1)
                      .-value)))

       (<! pituudet-haettu)

       (<! (paivita))

       (is (= "Tierekisterikohteiden pituus yhteensä: 10,00 km"
              (some-> (sel1 :#kohdeosien-pituus-yht) .-innerText)))

       ;; Tallennus nappi enabled
       (is (some-> (sel1 :#tallenna-paallystysilmoitus) .-disabled not))

       (let [tila-ok @lomake]

         ;; Muutetaan päällystystoimenpiteen RC% arvoksi ei-validi
         (sim/change (grid-solu "paallystysilmoitus-paallystystoimenpiteet" 0 4)
                     {:target {:value "888"}})

         (<! (paivita))

         (is (= 888 (get-in @lomake [:ilmoitustiedot :osoitteet 0 :rc%])))

         (is (= (get-in @lomake [:virheet :paallystystoimenpide 1 :rc%])
                '("Anna arvo välillä 0 - 100")))

         ;; Tallennus nappi disabled
         (is (some-> (sel1 :#tallenna-paallystysilmoitus) .-disabled))

         (<! (tarkista-asiatarkastus lomake))
         (reset! lomake tila-ok))

       (<! (paivita))

       ;; Muutetaan takaisin validiksi ja yritetään tallentaa
       (sim/change (grid-solu "paallystysilmoitus-paallystystoimenpiteet" 0 4)
                   {:target {:value "8"}})

       (<! (paivita))

       (click :#tallenna-paallystysilmoitus)

       ;; Tarkistetaan, että lähetettävät ilmoitustiedot ovat scheman mukaiset
       (is (s/validate pot/+paallystysilmoitus+
                       (:ilmoitustiedot (:paallystysilmoitus (<! tallennus)))))

       (done)))))
