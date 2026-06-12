(ns harja.views.urakka.suunnittelu.kalustoresurssit
  "Suunnittelun Kalustoresurssit-alasivu MHU26-urakoille."
  (:require [tuck.core :as tuck]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader-pieni]]
            [harja.domain.kalustoresurssit :as kalustoresurssit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.kalustoresurssit :as tiedot]))

(def ^:private ohjeteksti
  "Kirjaa kaluston määrä, jonka urakoitsija on tarjouksessaan sitonut tietylle hoitoluokalle. Tietoa käytetään pohjana kaluston käytön seurannassa.")

(defn- maara-solu
  "Renderöi kaluston määrän joko luku- tai muokkaustilassa.
   Muokkaustilassa palautetaan saavutettava numerokenttä, lukutilassa pelkkä teksti."
  [e! muokkaustila? {:keys [avain nimi]} arvo]
  (if muokkaustila?
    [:input.kalustoresurssi-maara
     {:type "number"
      :min 0
      :step 1
      :value (if (nil? arvo) "" arvo)
      :aria-label (str "Kaluston määrä, hoitoluokkaryhmä " nimi)
      :data-cy (str "kalustoresurssi-maara-" avain)
      :on-change #(e! (tiedot/->PaivitaMaara avain (-> % .-target .-value)))}]
    [:span {:data-cy (str "kalustoresurssi-maara-luku-" avain)}
     (if (nil? arvo) "–" arvo)]))

(defn- kalustoresurssi-taulukko [e! muokkaustila? maarat]
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
       :leveys 40
       :komponentti (fn [rivi _]
                      [maara-solu e! muokkaustila? rivi (:maara rivi)])}]
     rivit]))

(defn- painikkeet [e! muokkaustila? tallennus-kaynnissa?]
  [:div.painikkeet {:style {:margin-top "1rem"}}
   (if muokkaustila?
     [:<>
      [napit/yleinen-ensisijainen "Tallenna"
       #(e! (tiedot/->TallennaKalustoresurssit))
       {:disabled tallennus-kaynnissa?
        :data-cy "kalustoresurssit-tallenna"}]
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-toissijainen "Peruuta"
        #(e! (tiedot/->PeruutaMuokkaus))
        {:disabled tallennus-kaynnissa?
         :data-cy "kalustoresurssit-peruuta"}]]]
     [napit/yleinen-ensisijainen "Muokkaa"
      #(e! (tiedot/->AloitaMuokkaus))
      {:data-cy "kalustoresurssit-muokkaa"}])])

(defn- nakyma [e! app]
  (let [muokkaustila? (:muokkaustila? app)
        maarat (if muokkaustila?
                 (:muokkausbufferi app)
                 (:tallennetut-maarat app))]
    [:div.kalustoresurssit {:data-cy "kalustoresurssit"}
     [:h1 "Kalustoresurssit"]
     [:p.kalustoresurssit-ohje ohjeteksti]
     (if (:haku-kaynnissa? app)
       [ajax-loader-pieni "Ladataan kalustoresursseja…"]
       [:<>
        [kalustoresurssi-taulukko e! muokkaustila? maarat]
        [painikkeet e! muokkaustila? (:tallennus-kaynnissa? app)]])]))

(defn- kalustoresurssit* [e! _]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeKalustoresurssit)))
    (fn [e! app]
      [nakyma e! app])))

(defn kalustoresurssit []
  (tuck/tuck tila/suunnittelu-kalustoresurssit kalustoresurssit*))
