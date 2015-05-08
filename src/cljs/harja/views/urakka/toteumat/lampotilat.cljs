(ns harja.views.urakka.toteumat.lampotilat
  "Urakan toteumat: lämpötilat"
  (:require [reagent.core :refer [atom]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.tiedot.urakka.lampotilat :as lampotilat]
            [cljs.core.async :refer [<!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :as oikeudet]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.ikonit :as ikonit]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce nykyiset-lampotilat (atom nil))
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

(defn tallenna-muutos
  [uudet hoitokausi urakka]
  (log "Muokataan urakan" (:id urakka) "keskilämpöjä hoitokaudelle"
       (pvm/pvm (nth hoitokausi 0))
       " - "
       (pvm/pvm (nth hoitokausi 1)))
  (log "Uusi keskilämpötila on" (:keskilampo uudet) ", ja pitkä keskilämpö on" (:pitkalampo uudet))
  (go (let [uusi-lampo (<!(lampotilat/tallenna-lampotilat!
                (:id uudet)
                (:id urakka)
                (nth hoitokausi 0)
                (nth hoitokausi 1)
                (:keskilampo uudet)
                (:pitkalampo uudet)))]
        (reset! tallentaa-lampotilaa? false)
        (if (not (k/virhe? uusi-lampo))
          (do
            (if (nil? (some #(when (= (:id %) (:id uusi-lampo)) uusi-lampo) @nykyiset-lampotilat))
              ; Jos tällä id:llä ei löydy lämpötilaa, lämpötila on uusi ja se voidaan liittää listaan
              (do
                ;(log "Luo uusi rivi lämpötilalle")
                (swap! nykyiset-lampotilat conj uusi-lampo))
              ; Jos löytyi, täytyy lista rakentaa uusiksi
              (do
                ;(log "Päivitä olemassaolevaa lämpötilamerkintää")
                (reset! nykyiset-lampotilat (mapv
                                            (fn [vanha-lampo]
                                              (if (= (:id vanha-lampo) (:id uusi-lampo))
                                                uusi-lampo
                                                vanha-lampo))
                                            @nykyiset-lampotilat)))))))))

(defn lampotila-lomake
  [urakka lampotilat]
  (let [uudet-lampotilat (atom nil)
        aseta-lampotila (fn [l] (reset! uudet-lampotilat l))
        saa-muokata? (oikeudet/rooli-urakassa? "urakanvalvoja" (:id urakka))]

    (aseta-lampotila lampotilat)
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ urakka l]]
         (aseta-lampotila l))}

      (fn [urakka lampotilat]
        [lomake {:luokka :horizontal
                 :muokkaa! (fn [uusi]
                             (reset! uudet-lampotilat uusi))
                 :footer (if saa-muokata?
                           [:div.form-group
                           [:div.col-md-4
                            (if (and
                                  (kelvollinen-lampotila? (:pitkalampo @uudet-lampotilat))
                                  (kelvollinen-lampotila? (:keskilampo @uudet-lampotilat)))
                              [:button.nappi-ensisijainen {:name "tallenna" :on-click #(do
                                                                                        (.preventDefault %)
                                                                                        (reset! tallentaa-lampotilaa? true)
                                                                                        (tallenna-muutos
                                                                                          @uudet-lampotilat
                                                                                          @u/valittu-hoitokausi
                                                                                          urakka))}
                               (ikonit/search) " Tallenna"]

                              [:button.nappi-ensisijainen.disabled {:name "tallenna"}  "Tallenna"])

                            [:div {:style {:display :inline-block :width 55}}(when @tallentaa-lampotilaa? [ajax-loader])]
                            [:button.nappi-kielteinen {:name "peruuta" :on-click #(do
                                                                                   (.preventDefault %)
                                                                                   (reset! uudet-lampotilat lampotilat))}
                             (ikonit/remove)" Peruuta"]]])
                 }
         [{:otsikko "Keskilampo" :nimi :keskilampo :tyyppi :numero :leveys-col 2}
          {:otsikko "Pitkän aikavälin keskilämpö" :nimi :pitkalampo :tyyppi :numero :leveys-col 2}]
         @uudet-lampotilat]))))

(defn lampotilat [ur]
  (let [urakka (atom nil)
        aseta-urakka (fn [ur] (reset! urakka ur))
        valittu-hoitokausi u/valittu-hoitokausi]

    (go (reset! nykyiset-lampotilat (<! (lampotilat/hae-urakan-lampotilat (:id ur)))))
    (aseta-urakka ur)

    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ ur]]
         (aseta-urakka ur))}

      (fn [ur]
        (if (nil? @nykyiset-lampotilat)
          [ajax-loader]
        [:span
         [valinnat/urakan-hoitokausi ur]
         (when (oikeudet/rooli-urakassa? "urakanvalvoja" (:id urakka))
           [:button.nappi-toissijainen (ikonit/plus) " Lue arvot verkosta (FIXME)"]) ;fixme vaatii toteutuksen
         (if @valittu-hoitokausi
           [lampotila-lomake
            @urakka
            (etsi-lampotilat (nth @valittu-hoitokausi 0)
                             (nth @valittu-hoitokausi 1)
                             @nykyiset-lampotilat
                             (:id @urakka))])])))))

;Vanha lämpötilalomake. Säilytetty jos täältä jotain apua tarvii.
#_
(defn lampotila-lomake
  [urakka lampotilat]
  (let [uudet-lampotilat (atom nil)
        aseta-lampotila (fn [l] (reset! uudet-lampotilat l))
        saa-muokata? (oikeudet/rooli-urakassa? "urakanvalvoja" (:id urakka))]

    (aseta-lampotila lampotilat)
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ urakka l]]
         (aseta-lampotila l))}

    (fn [urakka lampotilat]
      ;(log "Nykyiset lämpötilat")
      ;(.dir js/console (clj->js @nykyiset-lampotilat))
      ;(.dir js/console (clj->js @uudet-lampotilat))
      [:form.form-inline
      [:div.form-group
       [:label.col-md-4.control-label
        {:for "keskilampo"}
        "Keskilämpötila"]
       [:div.col-md-4
        [:input#keskilampo.form-control.input-md
         (if saa-muokata?
           {:placeholder "", :type "text", :name "keskilampo" :value (:keskilampo @uudet-lampotilat)
            :on-change #(let [uusi-arvo (-> % .-target .-value)]
                         (swap! uudet-lampotilat assoc :keskilampo uusi-arvo))}

           {:placeholder "", :type "text", :name "keskilampo" :value (:keskilampo @uudet-lampotilat)
            :readOnly "readOnly"})]
        (if (not (and saa-muokata? (kelvollinen-lampotila? (:keskilampo @uudet-lampotilat))))
          (ikonit/warning-sign))]]

      [:div.form-group
       [:label.col-md-7.control-label
        {:for "pitkalampo"}
        "Pitkän aikavälin keskilämpötila"]
       [:div.col-md-4
        [:input#pitkalampo.form-control.input-md
         (if saa-muokata?
           {:placeholder "", :type "text", :name "pitkalampo" :value (:pitkalampo @uudet-lampotilat)
            :on-change #(let [uusi-arvo (-> % .-target .-value)]
                         (swap! uudet-lampotilat assoc :pitkalampo uusi-arvo))}
           {:placeholder "", :type "text", :name "pitkalampo" :value (:pitkalampo @uudet-lampotilat) :readOnly "readOnly"})]
        (if (not (and saa-muokata? (kelvollinen-lampotila? (:pitkalampo @uudet-lampotilat))))
          (ikonit/warning-sign))]]

       [:div.form-group
        (if (oikeudet/rooli-urakassa? "urakanvalvoja" (:id urakka))
        [:div.row-md-4
         (if (not (and saa-muokata? (kelvollinen-lampotila? (:pitkalampo @uudet-lampotilat))))
           [:button.nappi-ensisijainen.disabled {:name "tallenna"} "Tallenna"]
             [:button.nappi-ensisijainen {:name "tallenna" :on-click #(do
                                                                       (.preventDefault %)
                                                                       (reset! tallentaa-lampotilaa? true)
                                                                       (tallenna-muutos
                                                                         @uudet-lampotilat
                                                                         @u/valittu-hoitokausi
                                                                         urakka))}
              "Tallenna"])

             [:button.nappi-kielteinen {:name "peruuta" :on-click #(do
                                                                (.preventDefault %)
                                                                (reset! uudet-lampotilat lampotilat))}
              "Peruuta"]])]]))))