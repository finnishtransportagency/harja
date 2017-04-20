(ns harja.views.urakka.aikataulu
  "Ylläpidon urakoiden aikataulunäkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.aikataulu :as tiedot]
            [harja.ui.grid :as grid]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [vihje]]
            [harja.ui.lomake :as lomake]
            [cljs-time.core :as t]
            [harja.ui.napit :as napit]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.paallystysilmoitus :as pot]
            [harja.ui.yleiset :as yleiset]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.ui.viesti :as viesti]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.debug :as debug]
            [harja.ui.dom :as dom])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn valmis-tiemerkintaan [{:keys [kohde-id urakka-id vuosi paallystys-valmis? suorittava-urakka-annettu?]}]
  (let [valmis-tiemerkintaan-lomake (atom nil)
        valmis-tallennettavaksi? (reaction (some? (:valmis-tiemerkintaan @valmis-tiemerkintaan-lomake)))]
    (fn [{:keys [kohde-id urakka-id paallystys-valmis? suorittava-urakka-annettu?]}]
      [:div
       {:title (cond (not paallystys-valmis?) "Päällystys ei ole valmis."
                     (not suorittava-urakka-annettu?) "Tiemerkinnän suorittava urakka puuttuu."
                     :default nil)}
       [:button.nappi-ensisijainen.nappi-grid
        {:type "button"
         :disabled (or (not paallystys-valmis?)
                       (not suorittava-urakka-annettu?))
         :on-click
         (fn []
           (modal/nayta!
             {:otsikko "Kohteen merkitseminen valmiiksi tiemerkintään"
              :luokka "merkitse-valmiiksi-tiemerkintaan"
              :sulje-fn #(reset! valmis-tiemerkintaan-lomake nil) ; FIXME ei toimi?
              :footer [:div
                       [:span [:button.nappi-toissijainen
                               {:type "button"
                                :on-click #(do (.preventDefault %)
                                               (reset! valmis-tiemerkintaan-lomake nil)
                                               (modal/piilota!))}
                               "Peruuta"]
                        [napit/palvelinkutsu-nappi
                         "Merkitse"
                         #(do (log "[AIKATAULU] Merkitään kohde valmiiksi tiemerkintää")
                              (tiedot/merkitse-kohde-valmiiksi-tiemerkintaan
                                {:kohde-id kohde-id
                                 :tiemerkintapvm (:valmis-tiemerkintaan @valmis-tiemerkintaan-lomake)
                                 :urakka-id urakka-id
                                 :sopimus-id (first @u/valittu-sopimusnumero)
                                 :vuosi vuosi}))
                         {;:disabled (not @valmis-tallennettavaksi?) ; FIXME Ei päivity
                          :luokka "nappi-myonteinen"
                          :kun-onnistuu (fn [vastaus]
                                          (log "[AIKATAULU] Kohde merkitty valmiiksi tiemerkintää")
                                          (reset! tiedot/aikataulurivit vastaus)
                                          (modal/piilota!))}]]]}
             [:div
              [vihje "Toimintoa ei voi perua. Päivämäärän asettamisesta lähetetään sähköpostilla tieto tiemerkintäurakan urakanvalvojalle ja vastuuhenkilölle."]
              [lomake/lomake {:otsikko ""
                              :muokkaa! (fn [uusi-data]
                                          (reset! valmis-tiemerkintaan-lomake uusi-data))}
               [{:otsikko "Tiemerkinnän saa aloittaa"
                 :nimi :valmis-tiemerkintaan
                 :pakollinen? true
                 :tyyppi :pvm}]
               @valmis-tiemerkintaan-lomake]]))}
        "Aseta päivä\u00ADmäärä"]])))

(defn- paallystys-aloitettu-validointi
  "Validoinnit päällystys aloitettu -kentälle"
  [optiot]
  (as-> [[:pvm-kentan-jalkeen :aikataulu-kohde-alku
          "Päällystys ei voi alkaa ennen kohteen aloitusta."]] validointi

        ;; Päällystysnäkymässä validoidaan, että alku on annettu
        (if (= (:nakyma optiot) :paallystys)
          (conj validointi
                [:toinen-arvo-annettu-ensin :aikataulu-kohde-alku
                 "Päällystystä ei voi merkitä alkaneeksi ennen kohteen aloitusta."])
          validointi)))

(defn- oikeudet
  "Tarkistaa aikataulunäkymän tarvitsemat oikeudet"
  [urakka-id]
  (let [saa-muokata?
        (oikeudet/voi-kirjoittaa? oikeudet/urakat-aikataulu urakka-id)

        saa-asettaa-valmis-takarajan?
        (oikeudet/on-muu-oikeus? "TM-takaraja"
                                 oikeudet/urakat-aikataulu
                                 urakka-id
                                 @istunto/kayttaja)

        saa-merkita-valmiiksi?
        (oikeudet/on-muu-oikeus? "TM-valmis"
                                 oikeudet/urakat-aikataulu
                                 urakka-id
                                 @istunto/kayttaja)]
    {:saa-muokata? saa-muokata?
     :saa-asettaa-valmis-takarajan? saa-asettaa-valmis-takarajan?
     :saa-merkita-valmiiksi? saa-merkita-valmiiksi?
     :voi-tallentaa? (or saa-muokata?
                         saa-merkita-valmiiksi?
                         saa-asettaa-valmis-takarajan?)}))


(defn- otsikoi-aikataulurivit
  "Lisää väliotsikot valmiille, keskeneräisille ja aloittamatta oleville kohteille."
  [{:keys [valmis kesken aloittamatta] :as luokitellut-rivit}]
  (concat (when-not (empty? valmis)
            (into [(grid/otsikko "Valmiit kohteet")]
                  valmis))
          (when-not (empty? kesken)
            (into [(grid/otsikko "Keskeneräiset kohteet")]
                  kesken))
          (when-not (empty? aloittamatta)
            (into [(grid/otsikko "Aloittamatta olevat kohteet")]
                  aloittamatta))))

(def aikataulujana-varit
  {:kohde "cyan"
   :paallystys "green"
   :tiemerkinta "yellow"})

(defn- aikataulurivi-jana
  "Muuntaa aikataulurivin aikajankomponentin rivimuotoon."
  [{:keys [aikataulu-kohde-alku aikataulu-kohde-valmis
           aikataulu-paallystys-alku aikataulu-paallystys-loppu
           aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-loppu
           nimi]}]
  {:otsikko nimi
   :ajat (into []
               (remove nil?)
               [(when (and aikataulu-kohde-alku aikataulu-kohde-valmis)
                  {:vari (aikataulujana-varit :kohde)
                   :alku aikataulu-kohde-alku
                   :loppu aikataulu-kohde-valmis
                   :teksti (str "Koko kohde: "
                                (pvm/pvm aikataulu-kohde-alku) " \u2013 "
                                (pvm/pvm aikataulu-kohde-valmis))})
                (when (and aikataulu-paallystys-alku aikataulu-paallystys-loppu)
                  {:vari (aikataulujana-varit :paallystys)
                   :alku aikataulu-paallystys-alku
                   :loppu aikataulu-paallystys-loppu
                   :teksti (str "Päällystys: "
                                (pvm/pvm aikataulu-paallystys-alku) " \u2013 "
                                (pvm/pvm aikataulu-paallystys-loppu))})
                (when (and aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-loppu)
                  {:vari (aikataulujana-varit :tiemerkinta)
                   :alku aikataulu-tiemerkinta-alku
                   :loppu aikataulu-tiemerkinta-loppu
                   :teksti (str "Tiemerkintä: "
                                (pvm/pvm aikataulu-tiemerkinta-alku) " \u2013 "
                                (pvm/pvm aikataulu-tiemerkinta-loppu))})])})

(defn- min-ja-max-aika [ajat pad]
  (loop [min nil
         max nil
         [{:keys [alku loppu]} & ajat] ajat]
    (if-not alku
      [(and min (t/minus min (t/days pad)))
       (and max (t/plus max (t/days pad)))]
      (recur (cond
               (nil? min) alku
               (pvm/ennen? alku min) alku
               (pvm/ennen? loppu min) loppu
               :default min)
             (cond
               (nil? max) loppu
               (pvm/jalkeen? loppu max) loppu
               (pvm/jalkeen? alku max) alku
               :else max)
             ajat))))

(defn- kuukaudet
  "Ottaa sekvenssin järjestyksessä olevia päiviä ja palauttaa ne kuukausiin jaettuna.
  Palauttaa sekvenssin kuukausia {:alku alkupäivä :loppu loppupäivä :otsikko kk-formatoituna}."
  [paivat]
  (reduce
   (fn [kuukaudet paiva]
     (let [viime-kk (last kuukaudet)]
       (if (or (nil? viime-kk)
               (not (pvm/sama-kuukausi? (:alku viime-kk) paiva)))
         (conj kuukaudet {:alku paiva
                          :otsikko (pvm/koko-kuukausi-ja-vuosi paiva)
                          :loppu paiva})
         (update kuukaudet (dec (count kuukaudet))
                 assoc :loppu paiva))))
   []
   paivat))

(defn- aikajana [rivit]
  (r/with-let [tooltip (r/atom nil)]
    (let [rivin-korkeus 20
          leveys (* 0.95 @dom/leveys)
          alku-x 150
          alku-y 50
          korkeus (+ alku-y (* (count rivit) rivin-korkeus))
          kaikki-ajat (mapcat :ajat rivit)
          alkuajat (sort-by :alku pvm/ennen? kaikki-ajat)
          loppuajat (sort-by :loppu pvm/jalkeen? kaikki-ajat)
          [min-aika max-aika] (min-ja-max-aika kaikki-ajat 14)
          text-y-offset 8
          bar-y-offset 3
          bar-height (- rivin-korkeus 6)]
      (when (and min-aika max-aika)
        (let [paivat (pvm/paivat-valissa min-aika max-aika)
              paivia (count paivat)
              paivan-leveys (/ 100.0 paivia)
              rivin-y #(+ alku-y (* rivin-korkeus %))
              paiva-x #(+ alku-x (* (- leveys alku-x) (/ (pvm/paivia-valissa % min-aika) paivia)))
              kuukaudet (kuukaudet paivat)]
          [:div
           [:svg {:width leveys :height korkeus
                  :viewBox (str "0 0 " leveys " " korkeus)}


            [:g.aikajana-paivaviivat
             (for [p paivat
                   :let [x (paiva-x p)]]
               ^{:key p}
               [:line {:x1 x :y1 (- alku-y 5)
                       :x2 x :y2 korkeus
                       :style {:stroke "lightGray"}}])]

            (map-indexed
             (fn [i {:keys [ajat] :as rivi}]
               (let [y (rivin-y i)]
                 ^{:key i}
                 [:g
                  [:rect {:x (inc alku-x) :y (- y bar-y-offset)
                          :width (- leveys alku-x)
                          :height bar-height
                          :fill (if (even? i) "#f0f0f0" "#d0d0d0")}]
                  (map-indexed
                   (fn [j {:keys [alku loppu vari teksti]}]
                     (let [x (paiva-x alku)
                           width (- (paiva-x loppu) x)]
                       ^{:key j}
                       [:rect {:x x :y y
                               :width width
                               :height 10
                               :fill vari
                               :rx 3 :ry 3
                               :on-mouse-over #(reset! tooltip {:x (+ x (/ width 2))
                                                                :y (+ y 30)
                                                                :text teksti})
                               :on-mouse-out #(reset! tooltip nil)
                               }]))
                   ajat)
                  [:text {:x 0 :y (+ text-y-offset y)
                          :font-size 10}
                   (:otsikko rivi)]]))
             rivit)

            ;; Tehdään eri kuukausille väliotsikot
            (for [{:keys [alku loppu otsikko]} kuukaudet
                  :let [x (paiva-x alku)]]
              ^{:key otsikko}
              [:g
               [:text {:x (+ 5 x) :y 10} otsikko]
               [:line {:x1 x :y1 0
                       :x2 x :y2 korkeus
                       :style {:stroke "gray"}}]])

            ;; tooltip, jos on
            (when-let [tooltip @tooltip]
              (let [{:keys [x y text]} tooltip]
                [:g
                 [:rect {:x (- x 110) :y (- y 18) :width 220 :height 30
                         :rx 10 :ry 10
                         :style {:fill "wheat"}}]
                 [:text {:x x :y y
                         :text-anchor "middle"}
                  text]]))
            ]])))))

(defn aikataulu
  [urakka optiot]
  (komp/luo
    (komp/lippu tiedot/aikataulu-nakymassa?)
    (fn [urakka optiot]
      (let [{urakka-id :id :as ur} @nav/valittu-urakka
            sopimus-id (first @u/valittu-sopimusnumero)
            aikataulurivit @tiedot/aikataulurivit-suodatettu
            urakkatyyppi (:tyyppi urakka)
            vuosi @u/valittu-urakan-vuosi
            {:keys [voi-tallentaa? saa-muokata?
                    saa-asettaa-valmis-takarajan?
                    saa-merkita-valmiiksi?]} (oikeudet urakka-id)
            otsikoidut-aikataulurivit (otsikoi-aikataulurivit
                                       (tiedot/aikataulurivit-valmiuden-mukaan aikataulurivit urakkatyyppi))]
        [:div.aikataulu
         [valinnat/urakan-vuosi ur]
         [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
         [valinnat/tienumero yllapito-tiedot/tienumero]
         [aikajana (map aikataulurivi-jana aikataulurivit)]
         [grid/grid
          {:otsikko "Kohteiden aikataulu"
           :voi-poistaa? (constantly false)
           :voi-lisata? false
           :piilota-toiminnot? true
           :tyhja (if (nil? @tiedot/aikataulurivit)
                    [yleiset/ajax-loader "Haetaan kohteita..."] "Ei kohteita")
           :tallenna (if voi-tallentaa?
                       #(tiedot/tallenna-yllapitokohteiden-aikataulu
                          {:urakka-id urakka-id
                           :sopimus-id sopimus-id
                           :vuosi vuosi
                           :kohteet %
                           :epaonnistui-fn (fn [] (viesti/nayta! "Tallennus epäonnistui!"
                                                                 :warning
                                                                 viesti/viestin-nayttoaika-lyhyt))})
                       :ei-mahdollinen)}
          [{:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :leveys 3 :nimi :kohdenumero :tyyppi :string
            :pituus-max 128 :muokattava? (constantly false)}
           {:otsikko "Koh\u00ADteen nimi" :leveys 7 :nimi :nimi :tyyppi :string :pituus-max 128
            :muokattava? (constantly false)}
           {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi :tr-numero
            :tyyppi :positiivinen-numero :leveys 3 :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Ajo\u00ADrata"
            :nimi :tr-ajorata
            :muokattava? (constantly false)
            :tyyppi :string :tasaa :oikea
            :fmt #(pot/arvo-koodilla pot/+ajoradat+ %)
            :leveys 3}
           {:otsikko "Kais\u00ADta"
            :muokattava? (constantly false)
            :nimi :tr-kaista
            :tyyppi :string
            :tasaa :oikea
            :fmt #(pot/arvo-koodilla pot/+kaistat+ %)
            :leveys 3}
           {:otsikko "Aosa" :nimi :tr-alkuosa :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Aet" :nimi :tr-alkuetaisyys :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Losa" :nimi :tr-loppuosa :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "Let" :nimi :tr-loppuetaisyys :leveys 3
            :tyyppi :positiivinen-numero
            :tasaa :oikea
            :muokattava? (constantly false)}
           {:otsikko "YP-lk"
            :nimi :yllapitoluokka :leveys 4 :tyyppi :string
            :fmt yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi
            :muokattava? (constantly false)}
           (when (= (:nakyma optiot) :paallystys) ;; Asiakkaan mukaan ei tarvi näyttää tiemerkkareille
             {:otsikko "Koh\u00ADteen aloi\u00ADtus" :leveys 8 :nimi :aikataulu-kohde-alku
              :tyyppi :pvm :fmt pvm/pvm-opt
              :muokattava? #(and (= (:nakyma optiot) :paallystys) (constantly saa-muokata?))})
           {:otsikko "Pääl\u00ADlystyk\u00ADsen aloi\u00ADtus" :leveys 8 :nimi :aikataulu-paallystys-alku
            :tyyppi :pvm :fmt pvm/pvm-opt
            :muokattava? #(and (= (:nakyma optiot) :paallystys) (constantly saa-muokata?))
            :validoi (paallystys-aloitettu-validointi optiot)}
           {:otsikko "Pääl\u00ADlystyk\u00ADsen lope\u00ADtus" :leveys 8 :nimi :aikataulu-paallystys-loppu
            :tyyppi :pvm :fmt pvm/pvm-opt
            :muokattava? #(and (= (:nakyma optiot) :paallystys) (constantly saa-muokata?))
            :validoi [[:toinen-arvo-annettu-ensin :aikataulu-paallystys-alku
                       "Päällystystä ei ole merkitty aloitetuksi."]
                      [:pvm-kentan-jalkeen :aikataulu-paallystys-alku
                       "Valmistuminen ei voi olla ennen aloitusta."]
                      [:ei-tyhja-jos-toinen-arvo-annettu :valmis-tiemerkintaan
                       "Arvoa ei voi poistaa, koska kohde on merkitty valmiiksi tiemerkintään"]]}
           (when (= (:nakyma optiot) :paallystys)
             {:otsikko "Tie\u00ADmer\u00ADkin\u00ADnän suo\u00ADrit\u00ADta\u00ADva u\u00ADrak\u00ADka"
              :leveys 10 :nimi :suorittava-tiemerkintaurakka
              :tyyppi :valinta
              :fmt (fn [arvo]
                     (:nimi (some
                              #(when (= (:id %) arvo) %)
                              @tiedot/tiemerkinnan-suorittavat-urakat)))
              :valinta-arvo :id
              :valinta-nayta #(if % (:nimi %) "- Valitse urakka -")
              :valinnat @tiedot/tiemerkinnan-suorittavat-urakat
              :nayta-ryhmat [:sama-hallintayksikko :eri-hallintayksikko]
              :ryhmittely #(if (= (:hallintayksikko %) (:id (:hallintayksikko urakka)))
                             :sama-hallintayksikko
                             :eri-hallintayksikko)
              :ryhman-otsikko #(case %
                                 :sama-hallintayksikko "Hallintayksikön tiemerkintäurakat"
                                 :eri-hallintayksikko "Muut tiemerkintäurakat")
              :muokattava? (fn [rivi] (and saa-muokata? (:tiemerkintaurakan-voi-vaihtaa? rivi)))})
           {:otsikko "Val\u00ADmis tie\u00ADmerkin\u00ADtään" :leveys 10
            :nimi :valmis-tiemerkintaan :tyyppi :komponentti :muokattava? (constantly saa-muokata?)
            :komponentti (fn [rivi {:keys [muokataan?]}]
                           (if (:valmis-tiemerkintaan rivi)
                             [:span (pvm/pvm-opt (:valmis-tiemerkintaan rivi))]
                             (if (= (:nakyma optiot) :paallystys)
                               ;; Voi merkitä valmiiksi tiemerkintään vain päällystysurakassa
                               ;; Ei kuitenkaan jos gridi on muokkaustilassa, sillä päivämäärän asettaminen
                               ;; dialogista resetoi muokkaustilan.
                               (if muokataan?
                                 [:span]
                                 [valmis-tiemerkintaan
                                  {:kohde-id (:id rivi)
                                   :urakka-id urakka-id
                                   :vuosi vuosi
                                   :paallystys-valmis? (some? (:aikataulu-paallystys-loppu rivi))
                                   :suorittava-urakka-annettu? (some? (:suorittava-tiemerkintaurakka rivi))}])
                               [:span "Ei"])))}
           {:otsikko "Tie\u00ADmerkin\u00ADtä val\u00ADmis vii\u00ADmeis\u00ADtään"
            :leveys 6 :nimi :aikataulu-tiemerkinta-takaraja :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? (fn [rivi]
                           (and saa-asettaa-valmis-takarajan?
                                (:valmis-tiemerkintaan rivi)))}
           {:otsikko "Tiemer\u00ADkinnän aloi\u00ADtus"
            :leveys 6 :nimi :aikataulu-tiemerkinta-alku :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? (fn [rivi]
                           (and (= (:nakyma optiot) :tiemerkinta)
                                saa-merkita-valmiiksi?
                                (:valmis-tiemerkintaan rivi)))}
           {:otsikko "Tiemer\u00ADkinnän lope\u00ADtus"
            :leveys 6 :nimi :aikataulu-tiemerkinta-loppu :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? (fn [rivi]
                           (and (= (:nakyma optiot) :tiemerkinta)
                                (:aikataulu-tiemerkinta-alku rivi)
                                saa-merkita-valmiiksi?
                                (:valmis-tiemerkintaan rivi)))
            :validoi [[:toinen-arvo-annettu-ensin :aikataulu-tiemerkinta-alku
                       "Tiemerkintää ei ole merkitty aloitetuksi."]
                      [:pvm-kentan-jalkeen :aikataulu-tiemerkinta-alku
                       "Valmistuminen ei voi olla ennen aloitusta."]]}
           {:otsikko "Pääl\u00ADlystys\u00ADkoh\u00ADde val\u00ADmis" :leveys 6 :nimi :aikataulu-kohde-valmis :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? #(and (= (:nakyma optiot) :paallystys) (constantly saa-muokata?))
            :validoi [[:pvm-kentan-jalkeen :aikataulu-kohde-alku
                       "Kohde ei voi olla valmis ennen kuin se on aloitettu."]]}


           (when (istunto/ominaisuus-kaytossa? :tietyoilmoitukset)
             {:otsikko "Tie\u00ADtyö\u00ADilmoi\u00ADtus"
              :leveys 6
              :nimi :tietyoilmoitus
              :tyyppi :komponentti
              :komponentti (fn [{tietyoilmoitus-id :tietyoilmoitus-id :as kohde}]
                             [:button.nappi-toissijainen.nappi-grid
                              {:on-click #(siirtymat/avaa-tietyoilmoitus kohde)}
                              (if tietyoilmoitus-id
                                [ikonit/ikoni-ja-teksti (ikonit/livicon-eye) " Avaa"]
                                [ikonit/ikoni-ja-teksti (ikonit/livicon-plus) " Lisää"])])})]
          otsikoidut-aikataulurivit]
         (if (= (:nakyma optiot) :tiemerkinta)
           [vihje "Tiemerkinnän valmistumisesta lähetetään sähköpostilla tieto päällystysurakan urakanvalvojalle ja vastuuhenkilölle."])]))))
