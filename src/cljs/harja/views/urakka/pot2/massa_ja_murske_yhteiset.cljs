(ns harja.views.urakka.pot2.massa-ja-murske-yhteiset
  "Massojen ja murskeiden yhteistä UI-koodia"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.pot2 :as pot2-domain]
            [harja.ui.napit :as napit]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))


(def materiaali-jo-kaytossa-str "Materiaali on jo käytössä, eikä sitä voi enää poistaa.")


(defn- massan-murskeen-nimen-komp [ydin tarkennukset fmt toiminto-fn]
  (if (= :komponentti fmt)
    [(if toiminto-fn
       :a
       :span)
     {:on-click #(when toiminto-fn
                   (do
                     (.stopPropagation %)
                     (toiminto-fn)))
      :style {:cursor "pointer"}}
     [:span.bold ydin]
     [:span tarkennukset]]
    (str ydin tarkennukset)))

(defn massan-rikastettu-nimi
  "Formatoi massan nimen. Jos haluat Reagent-komponentin, anna fmt = :komponentti, muuten anna :string"
  ([massatyypit massa fmt]
   (massan-rikastettu-nimi massatyypit massa fmt nil))
  ([massatyypit massa fmt toiminto-fn]
   ;; esim AB16 (AN15, RC40, 2020/09/1234) tyyppi (raekoko, nimen tarkenne, DoP, Kuulamyllyluokka, RC%)
   (let [[ydin tarkennukset] (pot2-domain/massan-rikastettu-nimi massatyypit massa)]
     ;; vähän huonoksi ehkä meni tämän kanssa. Toinen funktiota kutsuva tarvitsee komponenttiwrapperin ja toinen ei
     ;; pitänee refaktoroida... fixme jos ehdit
     (if (= fmt :komponentti)
       [massan-murskeen-nimen-komp ydin tarkennukset fmt toiminto-fn]
       (massan-murskeen-nimen-komp ydin tarkennukset fmt toiminto-fn)))))

(defn murskeen-rikastettu-nimi
  ([mursketyypit murske fmt]
   (murskeen-rikastettu-nimi mursketyypit murske fmt nil))
  ([mursketyypit murske fmt toiminto-fn]
   ;; esim KaM LJYR 2020/09/3232 (0/40, LA30)
   ;; tyyppi Kalliomurske, tarkenne LJYR, rakeisuus 0/40, iskunkestävyys (esim LA30)
   (let [[ydin tarkennukset] (pot2-domain/mursken-rikastettu-nimi mursketyypit murske)]
     (if (= fmt :komponentti)
       [massan-murskeen-nimen-komp ydin tarkennukset fmt toiminto-fn]
       (massan-murskeen-nimen-komp ydin tarkennukset fmt toiminto-fn)))))

(defn materiaalin-kaytto
  [materiaali-kaytossa]
  (when-not (empty? materiaali-kaytossa)
    (let [lukitut-kohteet (filter #(when (str/includes? (:tila %) "lukittu")
                                     %)
                                  materiaali-kaytossa)
          materiaali-kaytossa (if-not (empty? lukitut-kohteet)
                                lukitut-kohteet
                                materiaali-kaytossa)]
      [:div
       [:h3 (if (not (empty? lukitut-kohteet))
              "Materiaalia on kirjattu päällystysilmoitukseen jonka tila on lukittu. Muokkaamista tai poistamista ei enää sallita. Lukitut kohteet: "
              (str "Materiaalia on kirjattu seuraavissa päällystysilmoituksissa: "))]
       [:ul
        (for [{kohdenumero :kohdenumero
               nimi :nimi
               kohteiden-lkm :kohteiden-lkm} materiaali-kaytossa]
          ^{:key kohdenumero}
          [:li (str "#" kohdenumero " " nimi " (" kohteiden-lkm " riviä)")])]])))

(defn puutelistaus [data muut-validointivirheet]
  (when-not (and (empty? (ui-lomake/puuttuvat-pakolliset-kentat data))
                 (empty? muut-validointivirheet))
    [:div
     [:div "Seuraavat pakolliset kentät pitää täyttää ennen tallentamista: "]
     [:ul
      (for [puute (concat
                    (ui-lomake/puuttuvien-pakollisten-kenttien-otsikot data)
                    muut-validointivirheet)]
        ^{:key (name puute)}
        [:li (name puute)])]]))

(defn tallenna-materiaali-nappi
  [materiaali-kaytossa toiminto-fn disabled tyyppi]
  (assert (#{:massa :murske} tyyppi) "Tallennettavan tyyppi oltava massa tai murske")
  (let [lukittu? (some #(str/includes? % "lukittu")
                       (map :tila materiaali-kaytossa))]
    [napit/tallenna
     "Tallenna"
     (let [materiaalin-str (if (= :murske tyyppi) "Murskeen" "Massan")]
       (if (empty? materiaali-kaytossa)
         toiminto-fn
         (when-not lukittu?
           (fn []
             (varmista-kayttajalta/varmista-kayttajalta
               {:otsikko (str materiaalin-str " tallentaminen")
                :sisalto
                [:div
                 [:div (str "Materiaali on käytössä päällystysilmoituksissa joita ei ole vielä lukittu, joten muokkaaminen on mahdollista. Jos muokkaat kyseistä materiaalia, tiedot päivittyvät kaikkialla missä materiaalia on käytetty.")]
                 [materiaalin-kaytto materiaali-kaytossa]
                 [:div "Haluatko varmasti tallentaa muutokset? Voit myös halutessasi luoda tästä massasta kopion ja muokata sitä."]]
                :toiminto-fn toiminto-fn
                :hyvaksy "Kyllä"})))))
     {:vayla-tyyli? true
      :luokka "suuri"
      :disabled (or disabled lukittu?)}]))

(defn poista-materiaali-nappi
  [materiaali-kaytossa toiminto-fn tyyppi]
  (assert (#{:massa :murske} tyyppi) "Poistettavan tyyppi oltava massa tai murske")
  (let [lukittu? (some #(str/includes? % "lukittu")
                       (map :tila materiaali-kaytossa))
        materiaalin-str (if (= :murske tyyppi) "Murskeen" "Massan")]
    [:div {:style {:width "160px"}}
     [napit/poista
      "Poista"
      (fn []
        (varmista-kayttajalta/varmista-kayttajalta
          {:otsikko (str materiaalin-str " poistaminen")
           :sisalto
           [:div (str "Haluatko ihan varmasti poistaa tämän " (clojure.string/lower-case materiaalin-str) "?")]
           :toiminto-fn toiminto-fn
           :hyvaksy "Kyllä"}))
      {:disabled (not (empty? materiaali-kaytossa))
       :vayla-tyyli? true
       :luokka "suuri"}]
     (when (and (not lukittu?)
                (not (empty? materiaali-kaytossa)))
       [yleiset/vihje materiaali-jo-kaytossa-str])]))

(defn tallennus-ja-puutelistaus
  [e! {:keys [data validointivirheet tallenna-fn voi-tallentaa?
              peruuta-fn poista-fn tyyppi id materiaali-kaytossa]}]
  [:div
   [puutelistaus (ui-lomake/puuttuvat-pakolliset-kentat data) validointivirheet]
   [:div.flex-row {:style {:margin-top "2rem" :align-items "start"}}
    [:div.tallenna-peruuta
     [tallenna-materiaali-nappi materiaali-kaytossa tallenna-fn
      voi-tallentaa?
      tyyppi]
     [napit/yleinen "Peruuta" :toissijainen peruuta-fn
      {:vayla-tyyli? true
       :luokka "suuri"}]]

    (when id
      [poista-materiaali-nappi materiaali-kaytossa poista-fn tyyppi])]
   [materiaalin-kaytto materiaali-kaytossa]])

(defn materiaalin-tiedot [materiaali {:keys [materiaalikoodistot]} toiminto-fn]
  [:div.pot2-materiaalin-tiedot
   (cond
     (some? (:harja.domain.pot2/murske-id materiaali))
     [murskeen-rikastettu-nimi (:mursketyypit materiaalikoodistot) materiaali :komponentti toiminto-fn]

     (some? (:harja.domain.pot2/massa-id materiaali))
     [massan-rikastettu-nimi (:massatyypit materiaalikoodistot) materiaali :komponentti toiminto-fn]

     :else nil)
   [napit/nappi "" toiminto-fn {:luokka "napiton-nappi"
                                :ikoni (ikonit/livicon-external)}]])

(defn massan-tai-murskeen-toiminnot [e! rivi]
  (let [muokkaus-event (if (contains? rivi :harja.domain.pot2/murske-id)
                         mk-tiedot/->MuokkaaMursketta
                         mk-tiedot/->MuokkaaMassaa)]
    [:span.pull-right
     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Muokkaa"]
      [napit/nappi ""
       #(e! (muokkaus-event rivi false))
       {:ikoninappi? true :luokka "klikattava"
        :ikoni (ikonit/livicon-pen)}]]

     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Luo kopio"]
      [napit/nappi ""
       #(e! (muokkaus-event rivi true))
       {:ikoninappi? true :luokka "klikattava"
        :ikoni (ikonit/livicon-duplicate)}]]]))