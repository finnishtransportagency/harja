(ns harja.views.urakka.muutokset.vaikutukset
  "Muutosten vaikutukset, uudet & vanhat"
  (:require [harja.fmt :as fmt]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))


(defn uusi-muutosten-vaikutus-rivit* [budjettitavoitteet valittu-hoitokausi indeksikorjaus-vahvistettu?]
  ["Hoitovuoden alun indeksikorjattu tavoitehinta"
   (if-not indeksikorjaus-vahvistettu?
     t-yhteiset/+indeksikorjausta-ei-vahvistettu-txt+
     (fmt/euro-opt (:hoitovuoden-alun-indeksikorjattu-tavoitehinta budjettitavoitteet)))

   (when (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet)
     [:ul
      [:li.harmaa-tumma-teksti "Edellisten hoitovuosien pysyvien muutosten osuus (indeksikorjattu)"]])
   (when (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet)
     (if-not indeksikorjaus-vahvistettu?
       t-yhteiset/+indeksikorjausta-ei-vahvistettu-txt+
       (fmt/euro-opt true true (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet))))

   "Kirjallisesti sovitut muutokset"
   (fmt/euro-opt true true (:kirjatut-muutokset-yht budjettitavoitteet))

   ^{:viiva-rivin-alle? true}
   [:div "Toteumiin perustuvat muutokset" [:br]
    "(vahvistetaan "
    [:a.klikattava.alleviivaa {:href "#"
                               :on-click
                               #(siirtymat/avaa-valikatselmus
                                  @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
                                  [(first valittu-hoitokausi)
                                   (second valittu-hoitokausi)])}
     "välikatselmuksessa."] ")"]
   (fmt/euro-opt true true (:toteumiin-perustuvat-muutokset-yht budjettitavoitteet))

   [:b "Yhteensä"]
   [:b (if (not indeksikorjaus-vahvistettu?)
         t-yhteiset/+muutosten-vaikutus-yhteensa-ei-saatavilla+
         (fmt/euro-opt (:muutosten-vaikutus-yht budjettitavoitteet)))]

   ^{:koko-rivin-leveys? true :tietorivi-luokka (str "keskita-rivin-sisalto"
                                                  (when indeksikorjaus-vahvistettu?
                                                    " piilota-rivin-sisalto"))}
   [yleiset/info-laatikko :neutraali
    [:span "Indeksikorjaus vahvistetaan "
     [:a.klikattava.alleviivaa {:href "#"
                                :on-click #(siirtymat/siirry-annettuun-valilehteen
                                             @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
                                             {:taso1 :urakat
                                              :taso2 :suunnittelu
                                              :taso3 :uusi-kustannussuunnitelma})}
      "kustannussuunnitelmassa."]]
    nil
    {:luokka "vihje-indeksikorjaus"}] ""])


(defn muutosten-vaikutukset-uusi
  "Yhteenveto muutosten vaikutuksista."
  [_e! {:keys [budjettitavoitteet valittu-hoitokausi haku-kaynnissa?] :as _app}]
  (let [{:keys [tavoitehinta-indeksikorjattu-per-hoitovuosi]} budjettitavoitteet
        indeksikorjaus-vahvistettu? (muutos-domain/hoitovuoden-indeksikorjaus-vahvistettu?
                                      tavoitehinta-indeksikorjattu-per-hoitovuosi valittu-hoitokausi)]
    [:div.muutosten-vaikutus
     (into []
       (concat
         [yleiset/tietoja {:class "muutosten-vaikutus-container body-text"
                           :tietorivi-luokka "padding-8"}
          [:h2 "Muutosten vaikutus tavoitehintaan"] ""]
         (if haku-kaynnissa?
           [[yleiset/ajax-loader "Ladataan yhteenvetoa..."] ""]
           (uusi-muutosten-vaikutus-rivit* budjettitavoitteet valittu-hoitokausi indeksikorjaus-vahvistettu?))))]))


(defn muutosten-vaikutukset-vanha
  "Yhteenveto muutosten vaikutuksista."
  [_e! {:keys [valittu-hoitokausi haku-kaynnissa?] :as _app}]
  [:div.muutosten-vaikutus
   (into []
     (concat
       [yleiset/tietoja {:class "muutosten-vaikutus-container body-text"
                         :tietorivi-luokka "padding-8"}
        [:h2 "Muutosten vaikutus tavoitehintaan"] ""]
       [:div "Test"]
       ))])
