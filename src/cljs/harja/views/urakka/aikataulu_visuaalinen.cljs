(ns harja.views.urakka.aikataulu-visuaalinen
  "Ylläpidon urakoiden aikataulunäkymän visuaalinen osa."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]

            [harja.domain.aikataulu :as aikataulu]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.viesti :as viesti]
            [harja.ui.kumousboksi :as kumousboksi]
            [harja.ui.aikajana :as aikajana]
            [harja.tiedot.urakka.aikataulu :as tiedot]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn visuaalinen-aikataulu
  [{:keys [urakka-id sopimus-id aikataulurivit aikajana? optiot
           vuosi voi-muokata-paallystys? voi-muokata-tiemerkinta?]}]
  (when aikajana?
    [:div
     [kumousboksi/kumousboksi {:nakyvissa? (kumousboksi/ehdotetaan-kumamomista?)
                               :nakyvissa-sijainti {:top "250px" :right 0 :bottom "auto" :left "auto"}
                               :piilossa-sijainti {:top "250px" :right "-170px" :bottom "auto" :left "auto"}
                               :kumoa-fn #(kumousboksi/kumoa-muutos! (fn [edellinen-tila kumottu-fn]
                                                                       (tiedot/tallenna-aikataulu
                                                                         urakka-id sopimus-id vuosi edellinen-tila
                                                                         (fn [vastaus]
                                                                           (reset! tiedot/aikataulurivit vastaus)
                                                                           (kumottu-fn)))))
                               :sulje-fn kumousboksi/ala-ehdota-kumoamista!}]
     [leijuke/otsikko-ja-vihjeleijuke 6 "Aikajana"
      {:otsikko "Visuaalisen muokkauksen ohje"}
      [leijuke/multipage-vihjesisalto
       [:div
        [:h6 "Aikajanan alun / lopun venytys"]
        [:figure
         [:img {:src "images/yllapidon_aikataulu_visuaalisen_muokkauksen_ohje_raahaus.gif"
                ;; Kuva ei lataudu heti -> leijukkeen korkeus määrittyy väärin -> avautumissuunta määrittyy väärin -> asetetaan height
                :style {:height "200px"}}]
         [:figcaption
          [:p "Tartu hiiren kursorilla kiinni janan alusta tai lopusta, raahaa eteen- tai taaksepäin pitämällä nappia pohjassa ja päästämällä irti. Muutos tallennetaan heti."]]]]
       [:div
        [:h6 "Aikajanan siirtäminen"]
        [:figure
         [:img {:src "images/yllapidon_aikataulu_visuaalisen_muokkauksen_ohje_raahaus2.gif"
                :style {:height "200px"}}]
         [:figcaption
          [:p "Tartu hiiren kursorilla kiinni janan keskeltä, raahaa eteen- tai taaksepäin pitämällä nappia pohjassa ja päästämällä irti. Muutos tallennetaan heti."]]]]
       [:div
        [:h6 "Usean aikajanan siirtäminen"]
        [:figure
         [:img {:src "images/yllapidon_aikataulu_visuaalisen_muokkauksen_ohje_raahaus3.gif"
                :style {:height "200px"}}]
         [:figcaption
          [:p "Paina CTRL-painike pohjaan ja klikkaa aikajanaa valitakseksi sen. Siirrä aikajanaa normaalisti, jolloin kaikki aikajanat liikkuvat samaan suuntaan yhtä paljon."]
          [:p "Voit perua janan valinnan CTRL-klikkaamalla sitä uudestaan. Voit perua kaikkien janojen valinnan klikkaamalla tyhjään alueeseen. Valinnat poistuvat myös sivua vaihtamalla."]]]]
       [:div
        [:h6 "Usean aikajanan alun / lopun venytys"]
        [:figure
         [:img {:src "images/yllapidon_aikataulu_visuaalisen_muokkauksen_ohje_raahaus4.gif"
                :style {:height "200px"}}]
         [:figcaption
          [:p "Paina CTRL-painike pohjaan ja klikkaa aikajanaa valitakseksi sen. Venytä aikajanaa normaalisti alusta tai lopusta, jolloin kaikki aikajanat venyvät samaan suuntaan yhtä paljon."]]]]]]
     [aikajana/aikajana
      {:ennen-muokkausta
       (fn [drag valmis! peru!]
         ;; Näytä modal jos raahattujen joukossa oli tiemerkinnän valmistumispvm, muuten tallenna suoraan
         (let [tiemerkinnan-valmistumiset (filter #(and (= (second (::aikajana/drag %)) :tiemerkinta)
                                                        (not (pvm/sama-pvm? (::aikajana/loppu %) (::aikajana/alkup-loppu %))))
                                                  drag)]
           (if (not (empty? tiemerkinnan-valmistumiset))
             ;; Tehdään muokaus modalin kautta.
             ;; Mikäli siirrettiin useampaa kohdetta, niin modalilla on tarkoitus antaa samat
             ;; mailitiedot kaikille muokatuille kohteille.
             (reset! tiedot/tiemerkinta-valmis-modal-data
                     {:valmis-fn (fn [lomakedata]
                                   ;; Lisätään modalissa kirjoitetut mailitiedot kaikille muokatuille kohteille
                                   (doseq [kohde-id (map #(first (::aikajana/drag %)) tiemerkinnan-valmistumiset)]
                                     (swap! tiedot/kohteiden-sahkopostitiedot assoc kohde-id
                                            {:urakka-vastaanottajat (keep #(when (and (:valittu? %) (:sahkoposti %))
                                                                             [(:urakka-id %) (:sahkoposti %)])
                                                                          (vals @tiedot/fimista-haetut-vastaanottajatiedot))
                                             :muut-vastaanottajat (yleiset/sahkopostiosoitteet-str->set
                                                                    (:muut-vastaanottajat lomakedata))
                                             :saate (:saate lomakedata)
                                             :kopio-itselle? (:kopio-itselle? lomakedata)}))
                                   (valmis!))
                      :peru-fn peru!
                      :nakyvissa? true
                      :kohteet (map (fn [raahaus]
                                      (-> {:id (first (::aikajana/drag raahaus))
                                           :nimi (::aikajana/kohde-nimi raahaus)
                                           :valmis-pvm (::aikajana/loppu raahaus)}))
                                    drag)
                      :urakka-id urakka-id
                      :vuosi vuosi
                      ;; Ota olemassa olevat sähköpostitiedot ja "yhdistä" ne koskemaan kaikkia
                      ;; muokattuja kohteita.
                      :lomakedata {:kopio-itselle? (or (some :kopio-lahettajalle? (map ::aikajana/sahkopostitiedot tiemerkinnan-valmistumiset))
                                                       true)
                                   :muut-vastaanottajat (zipmap (iterate inc 1)
                                                                (map #(-> {:sahkoposti %})
                                                                     (set (mapcat (fn [jana] (get-in jana [::aikajana/sahkopostitiedot :muut-vastaanottajat]))
                                                                                  tiemerkinnan-valmistumiset))))
                                   :saate (str/join " " (map #(get-in % [::aikajana/sahkopostitiedot :saate])
                                                             tiemerkinnan-valmistumiset))}})
             ;; Ei muokattujen tiemerkintöjen valmistumisia, tallenna suoraan
             (valmis!))))
       :muuta! (fn [drag]
                 (go (let [paivitetty-aikataulu (aikataulu/raahauksessa-paivitetyt-aikataulurivit aikataulurivit drag)
                           paivitetyt-aikataulu-idt (set (map :id paivitetty-aikataulu))
                           paivitettyjen-vanha-tila (filter #(paivitetyt-aikataulu-idt (:id %)) @tiedot/aikataulurivit)]

                       (if (aikataulu/aikataulu-validi? paivitetty-aikataulu)
                         (when (not (empty? paivitetty-aikataulu)) ; Tallenna ja ehdota kumoamista vain jos muutoksia
                           (<! (tiedot/tallenna-aikataulu urakka-id sopimus-id vuosi paivitetty-aikataulu
                                                          (fn [vastaus]
                                                            (reset! tiedot/aikataulurivit vastaus)
                                                            (kumousboksi/ehdota-kumoamista! paivitettyjen-vanha-tila)))))
                         (viesti/nayta! "Virheellistä päällystysajankohtaa ei voida tallentaa!" :danger)))))}
      (some->> aikataulurivit
               (map #(aikataulu/aikataulurivi-jana % {:nakyma (:nakyma optiot)
                                                      :urakka-id urakka-id
                                                      :voi-muokata-paallystys? voi-muokata-paallystys?
                                                      :voi-muokata-tiemerkinta? voi-muokata-tiemerkinta?
                                                      :nayta-tarkka-aikajana? @tiedot/nayta-tarkka-aikajana?
                                                      :nayta-valitavoitteet? @tiedot/nayta-valitavoitteet?}))
               (filter #(not (empty? (:harja.ui.aikajana/ajat %)))))]]))
