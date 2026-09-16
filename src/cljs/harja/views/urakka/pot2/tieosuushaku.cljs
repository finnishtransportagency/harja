(ns harja.views.urakka.pot2.tieosuushaku
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]))

(defn- osoite-tekstina
  [{:keys [tr-numero tr-ajorata tr-kaista tr-alkuosa tr-alkuetaisyys
           tr-loppuosa tr-loppuetaisyys]}]
  (str tr-numero " / " tr-alkuosa "/" tr-alkuetaisyys
       " - " tr-loppuosa "/" tr-loppuetaisyys
       ", ajorata " tr-ajorata ", kaista " tr-kaista))

(defn- hakukentta [e! hakuehdot otsikko avain data-cy]
  [kentat/tee-otsikollinen-kentta
   {:otsikko otsikko
    :arvo-atom (r/atom (get hakuehdot avain))
    :kentta-params {:tyyppi :numero
                    :kokonaisluku? true
                    :min 0
                    :data-cy data-cy
                    :toiminta-f #(e! (pot2-tiedot/->MuutaTieosuushaunEhtoa avain %))}}])

(defn- tulostaulukko [e! tieosuudet voi-lisata?]
  [grid/grid
   {:tyhja "Tieosuuksia ei löytynyt."
    :tunniste (juxt :tr-numero :tr-ajorata :tr-kaista
                    :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys)
    :gridin-luokka "pot2-tieosuushaku-grid"
    :piilota-muokkaus? true
    :data-cy "pot2-tieosuushaku-tulokset"}
   (cond-> []
     voi-lisata?
     (conj (grid/rivinvalintasarake
             {:otsikko "Valitse"
              :leveys 1
              :otsikkovalinta? true
              :kaikki-valittu?-fn #(and (seq tieosuudet)
                                        (every? :valittu? tieosuudet))
              :otsikko-valittu-fn #(e! (pot2-tiedot/->ValitseTieosuudet %))
              :rivi-valittu?-fn :valittu?
              :rivi-valittu-fn #(e! (pot2-tiedot/->ValitseTieosuus %1 %2))}))

     true
        (into [{:otsikko "Tie" :nimi :tr-numero :tyyppi :numero :tasaa :oikea :leveys 2}
          {:otsikko "Ajorata" :nimi :tr-ajorata :tyyppi :numero :tasaa :oikea :leveys 2}
          {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :numero :tasaa :oikea :leveys 2}
          {:otsikko "Aosa" :nimi :tr-alkuosa :tyyppi :numero :tasaa :oikea :leveys 2}
          {:otsikko "Aet" :nimi :tr-alkuetaisyys :tyyppi :numero :tasaa :oikea :leveys 2}
          {:otsikko "Losa" :nimi :tr-loppuosa :tyyppi :numero :tasaa :oikea :leveys 2}
          {:otsikko "Let" :nimi :tr-loppuetaisyys :tyyppi :numero :tasaa :oikea :leveys 2}]))
   tieosuudet])

(defn tieosuushaku
  [e! {:keys [tieosuushaku]} kohdeosat-atom voi-lisata?]
  (let [{:keys [auki? hakuehdot haetaan? tieosuudet
                kohteen-ulkopuolelle-jatkuvat virhe]} tieosuushaku
        valittuja? (some :valittu? tieosuudet)
        hakuehdot-puuttuvat? (some nil? ((juxt :tr-numero :tr-alkuosa :tr-loppuosa) hakuehdot))]
    [:div.pot2-tieosuushaku
     (if-not auki?
       nil
       [:div.pot2-tieosuushaku-paneeli {:data-cy "pot2-tieosuushaku"}
        [:div.pot2-tieosuushaku-otsikko
         [:h3 "Hae tieosuus"]
         [napit/sulje "Sulje paneeli"
          #(e! (pot2-tiedot/->SuljeTieosuushaku))
          {:data-cy "pot2-sulje-tieosuushaku"}]]
        [:div.pot2-tieosuushaku-ehdot
         [hakukentta e! hakuehdot "Tie" :tr-numero "pot2-tieosuushaku-tie"]
         [hakukentta e! hakuehdot "Alkuosa" :tr-alkuosa "pot2-tieosuushaku-alkuosa"]
         [hakukentta e! hakuehdot "Loppuosa" :tr-loppuosa "pot2-tieosuushaku-loppuosa"]
         ]
        [:div.pot2-tieosuushaku-ohje
         "Jos tieosat jatkuvat kohteen ulkopuolelle, listauksessa näytetään ainoastaan kohteen alku- ja loppuetäisyys, joka mahtuu kohteen sisälle."]
        (cond
          haetaan?
          [yleiset/ajax-loader "Haetaan tieosuuksia..."]

          virhe
          [yleiset/info-laatikko :varoitus "Tieosuuksien haku epäonnistui."]

          (some? tieosuudet)
          [:div.pot2-tieosuushaku-tulosalue
           [:div.pot2-tieosuushaku-tulosmaara
            (str "Tieosuuksia yhteensä " (count tieosuudet) " kpl")]
           [tulostaulukko e! tieosuudet voi-lisata?]
           (when (seq kohteen-ulkopuolelle-jatkuvat)
             [yleiset/info-laatikko
              :neutraali
              "Seuraavat tieosat jatkuvat kohteen ulkopuolelle. Listauksessa näkyy vain pääkohteen sisällä oleva osuus."
              (map osoite-tekstina kohteen-ulkopuolelle-jatkuvat)
              nil])
           (when voi-lisata?
             [napit/yleinen-ensisijainen
              "Lisää toimenpiteeksi"
              #(e! (pot2-tiedot/->LisaaValitutTieosuudet kohdeosat-atom))
              {:ikoni (ikonit/livicon-plus)
               :disabled (not valittuja?)
               :data-cy "pot2-lisaa-valitut-tieosuudet"}])])])]))