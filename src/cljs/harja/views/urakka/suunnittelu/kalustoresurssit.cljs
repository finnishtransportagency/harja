(ns harja.views.urakka.suunnittelu.kalustoresurssit
  "Suunnittelun Kalustoresurssit-alasivu MHU26-urakoille."
  (:require [tuck.core :as tuck]
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
  [:table.kalustoresurssit-taulukko {:data-cy "kalustoresurssit-taulukko"}
   [:caption "Kaluston määrä hoitoluokkaryhmittäin"]
   [:thead
    [:tr
     [:th {:scope "col" :id "kalustoresurssi-otsikko-ryhma"} "Hoitoluokkaryhmä"]
     [:th {:scope "col" :id "kalustoresurssi-otsikko-maara"} "Kaluston määrä"]]]
   [:tbody
    (for [{:keys [avain nimi] :as ryhma} kalustoresurssit/hoitoluokkaryhmat]
      ^{:key avain}
      [:tr
       [:th {:scope "row" :id (str "kalustoresurssi-rivi-" avain)} nimi]
       [:td {:headers (str "kalustoresurssi-otsikko-maara kalustoresurssi-rivi-" avain)}
        [maara-solu e! muokkaustila? ryhma (get maarat avain)]]])]])

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
