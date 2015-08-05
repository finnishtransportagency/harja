(ns harja.views.urakka.toteumat.lampotilat
  "Urakan toteumat: lämpötilat"
  (:require [reagent.core :refer [atom wrap]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.tiedot.urakka.lampotilat :as lampotilat]
            [cljs.core.async :refer [<!]]
            [harja.ui.komponentti :as komp]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :as oikeudet]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.napit :refer [palvelinkutsu-nappi]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.ikonit :as ikonit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.roolit :as roolit]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

;; Nykyiset lämpötilat hoitokausittain, mäppäys [alku loppu] => {:id ... :keskilampo ... :pitkalampo ...}
(defonce nykyiset-lampotilat (reaction<! [ur @nav/valittu-urakka]
                                         (when ur
                                           (go
                                             (let [lampotilat (<! (lampotilat/hae-urakan-lampotilat (:id ur)))]
                                               (log "LAMPOTILAT HAETTU: " (pr-str lampotilat))
                                               (zipmap (map (juxt :alkupvm :loppupvm) lampotilat)
                                                       lampotilat))))))

(defonce muokatut-lampotilat (reaction @nykyiset-lampotilat))

(defonce tallentaa-lampotilaa? (atom false))

(defn kelvollinen-lampotila?
  [lampo]
  "Tarkastaa että syöte vastaa nuroa xx.yy
  Alunperin tänne toteutettiin monta testiä jotka löytyvät kommenteista, mutta sitten otettiin käyttöön
  harja.ui.lomake. Lomake varmistaa että kenttään syötetään vain lukuja, mutta ei rajoita luvun suuruutta.
  Tietokantaan syötettynä lämmössä voi olla vain kaksi numeroa desimaalin molemmin puolin, eli
  täällä pitää tarkastaa, että luku on pienempi kuin 100.
  Vanhat testit kannattanee säilyttää kommenteissa."
  (let [l (str lampo)]
    (< (float lampo) 100)
    #_(and
      ; Tyhjä syöte pitää hyväksyä, se vain tarkoittaa että lämpötilaa ei ole vielä merkattu!
      ;(not (str/blank? l)) ;Onko syöte tyhjä, tai pelkkää whitespacea.
      (not (= (first l) \.)) ;Ensimmäinen merkki ei saa olla "." - tämä mahdollistaisi esimerkiksi syötteen ".400"
      (< (count l) 6) ;Pituus pitäisi olla maksimissaan viisi merkkiä (xx.yy)
      (< (count (str/split l #"\.")) 3) ; Pisteellä splitattuna, elementtejä saa olla maksimissaan 2
      (not (= (last l) \.)) ;Tarkista ettei viimeinen merkki ole "." - tällöin splitin tulos voisi silti olla 2
      (nil? (re-find #"[a-zA-Z]" l)) ; Etsi regexillä aakkoset
      (nil? (some
              (fn [liian_pitka] liian_pitka)
              (map #(> (count %) 2) (str/split l #"\."))))))) ;Tarkista, että pilkun molemmin puolin on max. 2 merkkiä


(defn etsi-lampotilat
  "Etsii aloitus- ja lopetuspäivämääriin sopivat keskilämmöt"
  [aloitus lopetus lampotilat urakka-id]
  (some (fn [l] (when
                  (and
                    (pvm/sama-pvm? aloitus (:alkupvm l))
                    (pvm/sama-pvm? lopetus (:loppupvm l))
                    (= urakka-id (:urakka l)))
                  l))
        lampotilat))

(defn tallenna-muutos [hoitokausi tulos]
  (let [uusi-lampo tulos]
    (when-not (k/virhe? uusi-lampo)
      (swap! nykyiset-lampotilat
             assoc hoitokausi tulos))))


(defn lampotila-lomake
  [urakka lampotilat]
  (let [saa-muokata?  (roolit/rooli-urakassa? roolit/urakanvalvoja
                                              (:id urakka))]
    
    (fn [urakka lampotilat]
      (let [hoitokausi @u/valittu-hoitokausi]
        [lomake {:luokka   :horizontal
                 :muokkaa! (fn [uusi]
                             (reset! lampotilat uusi))
                 :footer   (if saa-muokata?
                             [:div.form-group
                              [:div.col-md-4
                               [napit/palvelinkutsu-nappi
                                "Tallenna"
                                #(lampotilat/tallenna-lampotilat!
                                  (:id @lampotilat)
                                  (:id urakka)
                                  hoitokausi
                                  (:keskilampo @lampotilat)
                                  (:pitkalampo @lampotilat))
                                {:luokka       "nappi-ensisijainen"
                                 :disabled     (not (and
                                                     (::muokattu @lampotilat)
                                                     (kelvollinen-lampotila? (:pitkalampo @lampotilat))
                                                     (kelvollinen-lampotila? (:keskilampo @lampotilat))))
                                 :ikoni        (ikonit/search)
                                 :kun-onnistuu #(do
                                                  (viesti/nayta! "Tallentaminen onnistui" :success 1500)
                                                  (tallenna-muutos hoitokausi %))}]
                               
                               [:button.nappi-kielteinen {:name "peruuta"
                                                          :disabled (= @muokatut-lampotilat @nykyiset-lampotilat)
                                                          :on-click #(do
                                                                       (.preventDefault %)
                                                                       (reset! muokatut-lampotilat @nykyiset-lampotilat))}
                                (ikonit/remove) " Peruuta"]]])
                 }
         [{:otsikko "Keskilämpötila" :nimi :keskilampo :tyyppi :numero :leveys-col 2}
          {:otsikko "Pitkän aikavälin keskilämpötila" :nimi :pitkalampo :tyyppi :numero :leveys-col 2}]
         @lampotilat]))))

(defn lampotilat [ur]
  (let [urakka (atom nil)
        aseta-urakka (fn [ur] (reset! urakka ur))]

    (aseta-urakka ur)
    
    (komp/luo
     {:component-will-receive-props
      (fn [_ & [_ ur]]
        (aseta-urakka ur))}
     
     (fn [ur]
       (let [hoitokausi @u/valittu-hoitokausi]
         (if (nil? @nykyiset-lampotilat)
           [ajax-loader]
           [:span
            [valinnat/urakan-hoitokausi ur]
            (when (roolit/rooli-urakassa? roolit/urakanvalvoja
                                          (:id @urakka))
              [napit/palvelinkutsu-nappi "Hae ilmatieteenlaitokselta"
               #(lampotilat/hae-lampotilat-ilmatieteenlaitokselta (:id @urakka) (pvm/vuosi (first @u/valittu-hoitokausi)))
               {:ikoni true
                :kun-onnistuu (fn [{:keys [keskilampotila ilmastollinen-keskiarvo]}]
                                (log "SAATIIN ilmatieteenlaitokselta " keskilampotila " ja " ilmastollinen-keskiarvo)
                                (swap! muokatut-lampotilat update-in [hoitokausi]
                                       (fn [lampotilat]
                                         (assoc lampotilat
                                                :keskilampo keskilampotila
                                                :pitkalampo ilmastollinen-keskiarvo
                                                ::muokattu true))))}])
            
            (if @u/valittu-hoitokausi
              [lampotila-lomake
               @urakka
               (wrap (get @muokatut-lampotilat @u/valittu-hoitokausi)
                     #(swap! muokatut-lampotilat
                             assoc @u/valittu-hoitokausi
                             (assoc % ::muokattu true)))])]))))))
