(ns harja.tiedot.urakka.toteumat.suola
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.atom :refer-macros [reaction<!]]
            [reagent.ratom :refer [reaction]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(declare hae-toteumat hae-toteumat-tr-valille hae-materiaalit hae-toteumien-reitit! valittu-suolatoteuma? hae-toteuman-sijainti)

(defonce urakan-pohjavesialueet (atom nil))

(defonce urakan-rajoitusalueet (atom nil))

(defonce urakan-rajoitusalueiden-summatiedot (atom {}))
(defonce urakan-rajoitusalueiden-toteumat (atom {}))

(defonce suolatoteumissa? (atom false))

(defonce suodatin-valinnat (atom {:suola "Kaikki"}))

(defonce materiaalit
  (reaction<! [hae? @suolatoteumissa?]
              (when hae?
                (hae-materiaalit))))

(defonce
  ^{:doc "Valittu aikaväli materiaalien tarkastelulle"}
  valittu-aikavali (atom nil))

(defonce ui-lomakkeen-tila (atom nil))
(defonce lomakkeen-tila (atom nil))

(defonce toteumat
  (reaction<! [hae? @suolatoteumissa?
               urakka @nav/valittu-urakka
               #_#_aikavali @valittu-aikavali ;; kommentoitu, ettei haku käynnistyisi automaattisesti
               tr-vali @lomakkeen-tila]
              {:nil-kun-haku-kaynnissa? true}
              (when (and hae? urakka @valittu-aikavali)
                (go
                  (let [tr-vali (:tierekisteriosoite tr-vali)]
                    (if (and (:numero tr-vali)
                             (:loppuosa tr-vali))
                      (<! (hae-toteumat-tr-valille (:id urakka) @valittu-aikavali
                                                   (:numero tr-vali)
                                                   (:alkuosa tr-vali)
                                                   (:alkuetaisyys tr-vali)
                                                   (:loppuosa tr-vali)
                                                   (:loppuetaisyys tr-vali)))
                      (<! (hae-toteumat (:id urakka) @valittu-aikavali))))))))

(def valitut-toteumat (atom #{}))

(defonce valitut-toteumat-kartalla
  (reaction<! [toteumat (distinct (map :tid @valitut-toteumat))
               valitun-urakan-id (:id @nav/valittu-urakka)]
              (when valitun-urakan-id
                (hae-toteumien-reitit! valitun-urakan-id toteumat))))

(defonce lampotilojen-hallinnassa? (atom false))

(def karttataso-suolatoteumat (atom false))

(defn hae-toteuman-sijainti [toteuma]
  (some #(when (= (:tid toteuma) (:id %))
           (:sijainti %)) @valitut-toteumat-kartalla))

(def suolatoteumat-kartalla
  (reaction
    (when @karttataso-suolatoteumat
      (kartalla-esitettavaan-muotoon
        (let [kaikki-toteumat (apply concat (map :toteumaidt @toteumat))
              yksittaiset-toteumat (filter
                                     #(valittu-suolatoteuma? %)
                                     (map (fn [tid]
                                            {:tid tid})
                                          kaikki-toteumat))]
          (map #(assoc % :tyyppi-kartalla :suolatoteuma
                       :sijainti (hae-toteuman-sijainti %))
               yksittaiset-toteumat))
        #(constantly false)))))

(defn eriteltavat-toteumat [toteumat]
  (map #(hash-map :tid (:tid %)) toteumat))

(defn valittu-suolatoteuma? [toteuma]
  (some #(= (:tid toteuma) (:tid %))
        @valitut-toteumat))

(defn valitse-suolatoteumat [toteumat]
  (reset! valitut-toteumat
          (into #{}
                (concat @valitut-toteumat
                        (eriteltavat-toteumat toteumat)))))


;; FIXME: Vanha implementaatio (poista)
(defn hae-urakan-pohjavesialueet [urakka-id]
  {:pre [(int? urakka-id)]}
  (k/post! :hae-urakan-pohjavesialueet {:urakka-id urakka-id}))

;; FIXME: Vanha implementaatio (poista)
(defn hae-pohjavesialueen-suolatoteuma [pohjavesialue [alkupvm loppupvm]]
  (k/post! :hae-pohjavesialueen-suolatoteuma {:pohjavesialue pohjavesialue
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm}))


;; TODO: Tarvitaan palvelu rajoitusalueille. Feikataan data nyt tässä.
(defn hae-urakan-rajoitusalueet [urakka-id]
  {:pre [(int? urakka-id)]}
  (let [hoitokausi @urakka/valittu-hoitokausi
        _ (js/console.log "hae-rajoitusalueen-toteumien-summatiedot :: hoitokausi" (pr-str hoitokausi))
        hoitokauden-alkuvuosi (pvm/vuosi (first hoitokausi))
        _ (js/console.log "hae-rajoitusalueen-toteumien-summatiedot :: hoitokauden-alkuvuosi" (pr-str hoitokauden-alkuvuosi))
        urakka-id (-> @tila/yleiset :urakka :id)
        suolarajoitukset (k/post! :hae-suolarajoitukset {:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                         :urakka-id urakka-id})]
    #_(k/post! :hae-urakan-rajoitusalueet {:urakka-id urakka-id})


    #_ (go
      ;; Simuloi latausaikaa
      (<! (let [c (chan)]
            (js/setTimeout (fn [] (close! c)) 1000)
            c))
      [{:id 0
        :tr-osoite {:tie 25
                    :aosa 2
                    :aet 200
                    :losa 3
                    :let 2837}
        :pituus 4854
        :pituus_ajoradat 4857
        :pohjavesialueet [{:nimi "Hanko" :tunnus "107801"} {:nimi "Sandö-Grönvik" :tunnus "107803"}]
        :suolankayttoraja 6.6
        :kaytettava-formaattia? false
        :formiaatit_t_per_ajoratakm nil
        :talvisuola_t_per_ajoratakm 1.077}
       {:id 1
        :tr-osoite {:tie 104
                    :aosa 2
                    :aet 1882
                    :losa 2
                    :let 3430}
        :pituus 1548
        :pituus_ajoradat 1548
        :pohjavesialueet [{:nimi "Manibacka" :tunnus "160607"}]
        :suolankayttoraja 1.0
        :kaytettava-formaattia? true
        :formiaatit_t_per_ajoratakm 0.002
        :talvisuola_t_per_ajoratakm 0.457}
       {:id 2
        :tr-osoite {:tie 116
                    :aosa 1
                    :aet 590
                    :losa 1
                    :let 3508}
        :pituus 1548
        :pituus_ajoradat 1548
        :pohjavesialueet [{:nimi "Lohjanharju" :tunnus "142851 B"}]
        :suolankayttoraja 0.0
        :kaytettava-formaattia? true
        :formiaatit_t_per_ajoratakm nil
        :talvisuola_t_per_ajoratakm 0.221}])
    suolarajoitukset))

;; TODO: Tarvitaan palvelu rajoitusalueen toteumien summatietojen hakemiseen. Feikataan data nyt tässä.
(defn hae-rajoitusalueen-toteumien-summatiedot [rajoitusalue-id]
  {:pre [(int? rajoitusalue-id)]}
  ;; TODO: Ks. esimerkiksi kyselyt/materiaalit.sql "hae-suolatoteumien-summatiedot"
  #_(k/post! :hae-rajoitusalueen-toteumien-summatiedot {:urakka-id urakka-id})

  ;; Feikkaa rajoitusalueen id:llä haku.
  (go
    ;; Simuloi latausaikaa
    (<! (let [c (chan)]
          (js/setTimeout (fn [] (close! c)) 1000)
          c))
    (vec (remove nil? [(when (#{0} rajoitusalue-id)
                           {:id 0 ;; tai uniikki "rivinumero"
                            :pvm (pvm/nyt)
                            :maara-t 4.234
                            :materiaali-nimi "Talvisuola, rakeinen NaCl"
                            :materiaali-id 7
                            :toteuma-lkm 2
                            ;; Toteuma-idt tarvitaan, jotta voidaan hakea tarkemmat toteumatiedot, jos käyttöliittymässä klikataan auki
                            ;; summatietorivin vetolaatikko (joka sisältää siihen liittyvät toteumat)
                            :toteuma-idt [0 1]
                            :koneellinen? true})
                         (when (#{0} rajoitusalue-id)
                           {:id 1 ;; tai uniikki "rivinumero"
                            :pvm (pvm/nyt)
                            :maara-t 1.001
                            :materiaali-nimi "Talvisuolaliuos NaCl"
                            :materiaali-id 1
                            :toteuma-lkm 2
                            :toteuma-idt [2 3]
                            :koneellinen? true})
                         (when (#{1} rajoitusalue-id)
                           {:id 2 ;; tai uniikki "rivinumero"
                            :pvm (pvm/nyt)
                            :maara-t 0.707
                            :materiaali-nimi "Talvisuola, rakeinen NaCl"
                            :materiaali-id 7
                            :toteuma-lkm 1
                            :toteuma-idt [4]
                            :koneellinen? true})
                         (when (#{1} rajoitusalue-id)
                           {:id 3 ;; tai uniikki "rivinumero"
                            :pvm (pvm/nyt)
                            :maara-t 0.00309
                            :materiaali-nimi "Natriumformiaatti"
                            :materiaali-id 16
                            :toteuma-lkm 1
                            :toteuma-idt [5]
                            :koneellinen? true})
                         (when (#{2} rajoitusalue-id)
                           {:id 4 ;; tai uniikki "rivinumero"
                            :pvm (pvm/nyt)
                            :maara-t 0.342108
                            :materiaali-nimi "Talvisuolaliuos NaCl"
                            :materiaali-id 1
                            :toteuma-lkm 1
                            :toteuma-idt [6]
                            :koneellinen? true})]))))


;; TODO: Tarvitaan palvelu hae-rajoitusalueen-suolatoteumat. Feikataan data nyt tässä.
(defn hae-rajoitusalueen-suolatoteumat [toteuma-idt]
  {:pre [(seq toteuma-idt)]}
  #_(k/post! :hae-rajoitusalueen-suolatoteumat {:toteuma-idt toteuma-idt})


  ;; Feikataan toteuma-id:llä haku
  (go
    ;; Simuloi latausaikaa
    (<! (let [c (chan)]
          (js/setTimeout (fn [] (close! c)) 2000)
          c))
    (filterv #((set toteuma-idt) (:id %))
        [{:id 0
          :alkanut (pvm/nyt)
          :paattynyt (pvm/nyt)
          :maara-t 2.232
          :materiaali-nimi "Talvisuola, rakeinen NaCl"
          :materiaali-id 7
          :lisatieto "rdm40"}
         {:id 1
          :alkanut (pvm/nyt)
          :paattynyt (pvm/nyt)
          :maara-t 2.000
          :materiaali-nimi "Talvisuola, rakeinen NaCl"
          :materiaali-id 7
          :lisatieto "rdm40"}
         {:id 2
          :alkanut (pvm/nyt)
          :paattynyt (pvm/nyt)
          :maara-t 0.402
          :materiaali-nimi "Talvisuolaliuos NaCl"
          :materiaali-id 1
          :lisatieto "rdm40"}
         {:id 3
          :alkanut (pvm/nyt)
          :paattynyt (pvm/nyt)
          :maara-t 0.599
          :materiaali-nimi "Talvisuolaliuos NaCl"
          :materiaali-id 1
          :lisatieto "rdm40"}
         {:id 4
          :alkanut (pvm/nyt)
          :paattynyt (pvm/nyt)
          :maara-t 0.707
          :materiaali-nimi "Talvisuolaliuos NaCl"
          :materiaali-id 1
          :lisatieto "rdm40"}
         {:id 5
          :alkanut (pvm/nyt)
          :paattynyt (pvm/nyt)
          :maara-t 0.00309
          :materiaali-nimi "Natriumformiaatti"
          :materiaali-id 16
          :lisatieto "rdm40"}
         {:id 6
          :alkanut (pvm/nyt)
          :paattynyt (pvm/nyt)
          :maara-t 0.342108
          :materiaali-nimi "Talvisuolaliuos NaCl"
          :materiaali-id 1
          :lisatieto "rdm40"}])))

;; ------


(defn poista-valituista-suolatoteumista [toteumat]
  (reset! valitut-toteumat
          (into #{}
                (remove (into #{}
                              (eriteltavat-toteumat toteumat))
                        @valitut-toteumat))))

(defn hae-toteumat-tr-valille [urakka-id [alkupvm loppupvm] tie alkuosa alkuet loppuosa loppuet]
  {:pre [(int? urakka-id)]}
  (k/post! :hae-suolatoteumat-tr-valille {:urakka-id urakka-id
                                          :alkupvm alkupvm
                                          :loppupvm loppupvm
                                          :tie tie
                                          :alkuosa alkuosa
                                          :alkuet alkuet
                                          :loppuosa loppuosa
                                          :loppuet loppuet}))

(defn hae-toteumat [urakka-id [alkupvm loppupvm]]
  {:pre [(int? urakka-id)]}
  (k/post! :hae-suolatoteumat {:urakka-id urakka-id
                               :alkupvm alkupvm
                               :loppupvm loppupvm}))

(defn hae-toteumien-reitit! [urakka-id toteuma-idt]
  {:pre [(int? urakka-id)]}
  (when (not (empty? toteuma-idt))
    (k/post! :hae-toteumien-reitit {:idt toteuma-idt :urakka-id urakka-id})))

(defn tallenna-toteumat [urakka-id sopimus-id rivit]
  {:pre [(int? urakka-id)]}
  (let [tallennettavat (into [] (->> rivit
                                     (filter (comp not :koskematon))
                                     (map #(assoc % :paattynyt (:alkanut %)))))]
    (k/post! :tallenna-suolatoteumat
             {:urakka-id urakka-id
              :sopimus-id sopimus-id
              :toteumat tallennettavat})))

(defn hae-materiaalit []
  (k/get! :hae-suolamateriaalit))

(defn hae-lampotilat-ilmatieteenlaitokselta [talvikauden-alkuvuosi]
  (k/post! :hae-lampotilat-ilmatieteenlaitokselta {:vuosi talvikauden-alkuvuosi} nil true))

(defn hae-teiden-hoitourakoiden-lampotilat [hoitokausi]
  (k/post! :hae-teiden-hoitourakoiden-lampotilat {:hoitokausi hoitokausi}))

(def hoitokaudet
  (vec
    (let [nyt (pvm/nyt)
          tama-vuosi (pvm/vuosi nyt)
          ;; sydäntalvi on joulu-helmikuu, tarjotaan sydäntalven keskilämpöltilan hakua aikaisintaan
          ;; maaliskuussa. Ei tarpeen huomioida karkauspäivää koska manuaalinen integraatio.
          sydantalvi-ohi? (pvm/jalkeen? nyt (pvm/->pvm (str "28.2." tama-vuosi)))
          vanhin-haettava-vuosi 2005]
      (for [vuosi (range vanhin-haettava-vuosi
                         (if sydantalvi-ohi?
                           tama-vuosi
                           (dec tama-vuosi)))]
        [(pvm/hoitokauden-alkupvm vuosi) (pvm/hoitokauden-loppupvm (inc vuosi))]))))

(defonce valittu-hoitokausi (atom (last hoitokaudet)))

(defn valitse-hoitokausi! [tk]
  (reset! valittu-hoitokausi tk))

(defonce hoitourakoiden-lampotilat
  (reaction<! [lampotilojen-hallinnassa? @lampotilojen-hallinnassa?
               valittu-hoitokausi @valittu-hoitokausi]
              {:nil-kun-haku-kaynnissa? true}
              (when (and lampotilojen-hallinnassa?
                         valittu-hoitokausi)
                (hae-teiden-hoitourakoiden-lampotilat valittu-hoitokausi))))

(defn hae-urakan-suolasakot-ja-lampotilat [urakka-id]
  {:pre [(int? urakka-id)]}
  (k/post! :hae-urakan-suolasakot-ja-lampotilat urakka-id))

(defn tallenna-teiden-hoitourakoiden-lampotilat [hoitokausi lampotilat]
  (let [lampotilat (mapv #(assoc % :alkupvm (first hoitokausi)
                                   :loppupvm (second hoitokausi))
                         (vec (vals lampotilat)))]
    (log "tallenna lämpötilat: " (pr-str lampotilat))
    (k/post! :tallenna-teiden-hoitourakoiden-lampotilat {:hoitokausi hoitokausi
                                                         :lampotilat lampotilat})))
