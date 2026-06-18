(ns harja.views.urakka.muutokset.vaikutukset
  "Muutosten vaikutukset, uudet & vanhat"
  (:require [harja.fmt :as fmt]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))

(defn muutosten-vaikutus-laskutusrajaan-rivit* [budjettitavoitteet laskutusrajan-tarkistukset indeksikorjaus-vahvistettu?]
  (let [{:keys [laskutusraja laskutusraja_alkuperainen]} budjettitavoitteet
        muutoksia_tehty (and laskutusraja laskutusraja_alkuperainen (> laskutusraja laskutusraja_alkuperainen))]
    [^{:koko-rivin-leveys? true :tietorivi-luokka (str "keskita-rivin-sisalto"
                                                    (when indeksikorjaus-vahvistettu?
                                                      " piilota-rivin-sisalto"))}

     [yleiset/info-laatikko :vahva-ilmoitus
      "Hoitovuoden alun indeksikorjattua tavoitehintaa ei ole vahvistettu"
      "Laskenta tehdään ei-vahvistetuilla tiedoilla, jotka saattavat päivittyä, kun hoitovuoden alun indeksikorjattu tavoitehinta vahvistetaan."
      nil
      {:ikoni-fn #(ikonit/harja-icon-status-alert)}] [:div ""]

     (when muutoksia_tehty
       ^{:tietorivi-luokka "viivan-alla"} [:div "Laskutusraja hoitovuoden alussa"])
     (when muutoksia_tehty
       ^{:tietorivi-luokka "viivan-alla"} (fmt/euro-opt true false laskutusraja_alkuperainen))

     (when muutoksia_tehty
       ^{:viiva-rivin-alle? true :tietorivi-luokka "viivan-ylla"} [:div "Laskutusrajan automaattiset tarkistukset"])
     (when muutoksia_tehty
       ^{:viiva-rivin-alle? true :tietorivi-luokka "viivan-ylla"} (fmt/euro-opt true true (some-> laskutusrajan-tarkistukset last :laskutusrajan-tarkistus)))

     (when (and laskutusraja (= laskutusraja laskutusraja_alkuperainen))
       ^{:viiva-rivin-alle? true :tietorivi-luokka "viivan-ylla"} [:div "Ei vaikutusta laskutusrajaan"])
     (when (and laskutusraja (= laskutusraja laskutusraja_alkuperainen))
       ^{:viiva-rivin-alle? true :tietorivi-luokka "viivan-ylla"} [:div ""])

     ^{:koko-rivin-leveys? true :viiva-rivin-alle? true :tietorivi-luokka (str "keskita-rivin-sisalto notifikaatio-viivan-ylla"
                                                    (when indeksikorjaus-vahvistettu?
                                                      " piilota-rivin-sisalto"))}

     (if muutoksia_tehty
       ^{:tietorivi-luokka "viivan-alla"} [:span.semibold "Tarkistettu laskutusraja"] ^{:tietorivi-luokka "viivan-alla"} [:span.semibold "Laskutusraja"])
     (if laskutusraja
       ^{:tietorivi-luokka "viivan-alla"} [:span.semibold (fmt/euro-opt true false laskutusraja)] ^{:tietorivi-luokka "viivan-alla"} [:span.semibold "Ei saatavilla"])]))

(defn uusi-muutosten-vaikutus-rivit* [budjettitavoitteet valittu-hoitokausi indeksikorjaus-vahvistettu?]
  (let [aiemmat-pysyvat-muutokset (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet)
        kirjatut-muutokset (:kirjatut-muutokset-yht budjettitavoitteet)
        toteumiin-perustuvat-muutokset (:toteumiin-perustuvat-muutokset-yht budjettitavoitteet)]
    [^{:koko-rivin-leveys? true :tietorivi-luokka (str "keskita-rivin-sisalto"
                                                    (when indeksikorjaus-vahvistettu?
                                                      " piilota-rivin-sisalto"))}
     [yleiset/info-laatikko :vahva-ilmoitus
      "Hoitovuoden alun indeksikorjattua tavoitehintaa ei ole vahvistettu"
      "Laskentaa ei voida tehdä kaikkien tietojen osalta."
      nil
      {:ikoni-fn #(ikonit/harja-icon-status-alert)}] [:div ""]

     (when (and (= aiemmat-pysyvat-muutokset 0) (= kirjatut-muutokset 0) (= toteumiin-perustuvat-muutokset 0) indeksikorjaus-vahvistettu?)
       ^{:viiva-rivin-alle? true :tietorivi-luokka "viivan-ylla"} [:div "Ei vaikutusta tavoitehintaan"])
     (when (and (= aiemmat-pysyvat-muutokset 0) (= kirjatut-muutokset 0) (= toteumiin-perustuvat-muutokset 0) indeksikorjaus-vahvistettu?)
       ^{:viiva-rivin-alle? true :tietorivi-luokka "viivan-ylla"} [:div ""])

     ^{:tietorivi-luokka (when (or (not indeksikorjaus-vahvistettu?) (and (= aiemmat-pysyvat-muutokset 0) (= kirjatut-muutokset 0)
                                                                       (= toteumiin-perustuvat-muutokset 0) indeksikorjaus-vahvistettu?))
                           "viivan-alla")}
     [:span "Hoitovuoden alun indeksikorjattu tavoitehinta"]
     (if-not indeksikorjaus-vahvistettu?
       t-yhteiset/+indeksikorjausta-ei-vahvistettu-txt+
       (fmt/euro-opt (:hoitovuoden-alun-indeksikorjattu-tavoitehinta budjettitavoitteet)))

     (when (not= aiemmat-pysyvat-muutokset 0)
       [:ul
        [:li.harmaa-tumma-teksti "Edellisten hoitovuosien pysyvien muutosten osuus (indeksikorjattu)"]])
     (when (not= aiemmat-pysyvat-muutokset 0)
       (if-not indeksikorjaus-vahvistettu?
         t-yhteiset/+indeksikorjausta-ei-vahvistettu-txt+
         [:span.harmaa-tumma-teksti (fmt/euro-opt true true (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet))]))

     "Kirjallisesti sovitut muutokset"
     (fmt/euro-opt true true (:kirjatut-muutokset-yht budjettitavoitteet))

     ^{:viiva-rivin-alle? true :tietorivi-luokka "viivan-ylla"}
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

     ^{:tietorivi-luokka "viivan-alla"} [:span.semibold "Yhteensä"]
     ^{:tietorivi-luokka "viivan-alla"} [:span.semibold (if (not indeksikorjaus-vahvistettu?)
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
      {:luokka "vihje-indeksikorjaus"}] ""]))

(defn muutosten-vaikutukset-uusi
  "Yhteenveto muutosten vaikutuksista."
  [{:keys [budjettitavoitteet valittu-hoitokausi haku-kaynnissa? laskutusrajan-tarkistukset] :as _app}]
  (let [{:keys [tavoitehinta-indeksikorjattu-per-hoitovuosi laskutusraja_kaytossa?]} budjettitavoitteet
        indeksikorjaus-vahvistettu? (muutos-domain/hoitovuoden-indeksikorjaus-vahvistettu?
                                      tavoitehinta-indeksikorjattu-per-hoitovuosi valittu-hoitokausi)]
    [:div.muutosten-vaikutus
     (into []
       (concat
         [yleiset/tietoja {:class "muutosten-vaikutus-container body-text"
                           :tietorivi-luokka "padding-8"}
          [:h3 "Muutosten vaikutus tavoitehintaan"] ""]
         (if haku-kaynnissa?
           [[yleiset/ajax-loader "Ladataan yhteenvetoa..."] ""]
           (uusi-muutosten-vaikutus-rivit* budjettitavoitteet valittu-hoitokausi indeksikorjaus-vahvistettu?))))
     (when laskutusraja_kaytossa?
       (into []
         (concat
           [yleiset/tietoja {:class "muutosten-vaikutus-container body-text"
                             :tietorivi-luokka "padding-8"}
            [:h3 "Muutosten vaikutus laskutusrajaan"] ""]
           (if haku-kaynnissa?
             [[yleiset/ajax-loader "Ladataan yhteenvetoa..."] ""]
             (muutosten-vaikutus-laskutusrajaan-rivit* budjettitavoitteet laskutusrajan-tarkistukset indeksikorjaus-vahvistettu?)))))]))


(defn muutosten-vaikutukset-vanha
  "Yhteenveto muutosten vaikutuksista."
  [{:keys [valittu-hoitokausi haku-kaynnissa?
           hoitovuoden-indeksikorjattu-tavoitehinta tavoitehinnan-muutokset-yhteensa] :as _app}]
  [:div.muutosten-vaikutus
   (into []
     (concat
       [yleiset/tietoja {:class "muutosten-vaikutus-container body-text"
                         :tietorivi-luokka "padding-8"}
        [:h2 "Muutosten vaikutus tavoitehintaan"] ""]

       (if haku-kaynnissa?
         [[yleiset/ajax-loader "Ladataan yhteenvetoa..."] ""]
         ["Hoitovuoden alun indeksikorjattu tavoitehinta"
          (fmt/euro-opt true true hoitovuoden-indeksikorjattu-tavoitehinta)


          ^{:viiva-rivin-alle? true}
          [:div "Tavoitehinnan muutokset"]
          (fmt/euro-opt true true tavoitehinnan-muutokset-yhteensa)

          [:b "Yhteensä"]
          [:b (fmt/euro-opt true true (+ hoitovuoden-indeksikorjattu-tavoitehinta tavoitehinnan-muutokset-yhteensa))]

          ""])))])
