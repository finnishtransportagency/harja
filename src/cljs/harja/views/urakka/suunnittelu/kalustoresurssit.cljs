(ns harja.views.urakka.suunnittelu.kalustoresurssit
  "Suunnittelun Kalustoresurssit-alasivu MHU26-urakoille."
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader-pieni]]
            [harja.domain.kalustoresurssit :as kalustoresurssit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.kalustoresurssit :as tiedot]))

(def ^:private ohjeteksti
  "Kirjaa kaluston määrä, jonka urakoitsija on tarjouksessaan sitonut tietylle hoitoluokalle. Tietoa käytetään pohjana kaluston käytön seurannassa.")

(defn- maara-solu
  "Renderöi kaluston määrän Väylä-tyylisellä numerokentällä.
  Lukutilassa näytetään arvo tekstinä ilman harmaata taustaa käyttämällä
  `kentat/nayta-arvo`-komponenttia." 
  [e! {:keys [avain nimi]} arvo disabled?]
  (let [kentta-opts {:tyyppi :positiivinen-numero
                     :kokonaisluku? true
                     :desimaalien-maara 0
                     :veda-oikealle? true
                     :vayla-tyyli? true
                     :koko 12
                     :data-cy (str "kalustoresurssi-maara-" avain)
                     :aria-label (str "Kaluston määrä, hoitoluokkaryhmä " nimi)}]
    (if disabled?
      [kentat/nayta-arvo kentta-opts (kentat/vain-luku-atomina arvo)]
      [kentat/tee-kentta
       kentta-opts
      (r/wrap arvo #(e! (tiedot/->PaivitaMaara avain %)))])))

(defn- kalustoresurssi-taulukko [e! maarat luku-tila?]
  (let [rivit (mapv (fn [{:keys [avain nimi]}]
                      {:avain avain
                       :nimi nimi
                       :maara (get maarat avain)})
                kalustoresurssit/hoitoluokkaryhmat)]
    [grid/grid
     {:otsikko ""
      :tunniste :avain
      :data-cy "kalustoresurssit-taulukko"
      :piilota-toiminnot? true
      :tyhja "Ei hoitoluokkaryhmiä."
      :rivi-jalkeen-fn (fn [rivit]
                         (let [yhteensa (reduce + 0 (keep :maara rivit))]
                           [{:teksti "Yhteensä" :luokka "yhteensa"}
                            {:teksti (str yhteensa) :luokka "yhteensa" :tasaa :oikea}]))}
     [{:otsikko "Hoitoluokka"
       :nimi :nimi
       :tyyppi :string
       :leveys 60}
      {:otsikko "Kaluston määrä (kpl)"
       :nimi :maara
       :tyyppi :komponentti
       :tasaa :oikea
       :leveys 10
       :komponentti (fn [rivi _]
                      [maara-solu e! rivi (:maara rivi) luku-tila?])}]
     rivit]))

(defn- painikkeet [e! tallennus-kaynnissa? muutoksia? voi-peruuttaa?]
  [:div.painikkeet {:style {:margin-top "1rem"}}
   [napit/yleinen-ensisijainen "Tallenna"
    #(e! (tiedot/->TallennaKalustoresurssit))
    {:disabled (or tallennus-kaynnissa? (not muutoksia?))
     :data-cy "kalustoresurssit-tallenna"}]
   [:span {:style {:margin-left "1rem"}}
    [napit/yleinen-toissijainen "Peruuta"
     #(e! (tiedot/->PeruutaMuokkaus))
     {:disabled (not voi-peruuttaa?)
      :data-cy "kalustoresurssit-peruuta"}]]])

(defn- nakyma [e! app]
  (let [muokkaustila? (:muokkaustila? app)
        tallennettu? (seq (:tallennetut-maarat app))
        luku-tila? (and tallennettu? (not muokkaustila?))
        maarat (:muokkausbufferi app)
        muutoksia? (not= maarat (:tallennetut-maarat app))
        voi-peruuttaa? (boolean muokkaustila?)]
    [:div.kalustoresurssit {:data-cy "kalustoresurssit"}
     [:div {:style {:display "flex" :justify-content "space-between" :align-items "center"}}
      [:h1 "Kalustoresurssit"]
      (when luku-tila?
        [napit/yleinen-toissijainen "Muokkaa"
         #(e! (tiedot/->AloitaMuokkaus))
         {:data-cy "kalustoresurssit-muokkaa"}])]
     [:p.kalustoresurssit-ohje ohjeteksti]
     (if (:haku-kaynnissa? app)
       [ajax-loader-pieni "Ladataan kalustoresursseja…"]
       [:<>
        [kalustoresurssi-taulukko e! maarat luku-tila?]
        (when-not luku-tila?
          [painikkeet e! (:tallennus-kaynnissa? app) muutoksia? voi-peruuttaa?])])]))

(defn- kalustoresurssit* [e! _]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeKalustoresurssit)))
    (fn [e! app]
      [nakyma e! app])))

(defn kalustoresurssit []
  (tuck/tuck tila/suunnittelu-kalustoresurssit kalustoresurssit*))
