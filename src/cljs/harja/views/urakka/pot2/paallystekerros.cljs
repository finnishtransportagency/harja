(ns harja.views.urakka.pot2.paallystekerros
  "POT2-lomakkeen päällystekerros"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.pot2 :as pot2-domain]
    [harja.domain.tierekisteri :as tr]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.loki :refer [log]]
    [harja.ui.debug :refer [debug]]
    [harja.ui.grid :as grid]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.yleiset :refer [ajax-loader]]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as pot2-yhteiset]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.ui.yleiset :as yleiset]
    [harja.validointi :as v]
    [harja.fmt :as fmt])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn validoi-paallystekerros
  [rivi taulukko]
  (let [{:keys [perustiedot tr-osien-tiedot]} (:paallystysilmoitus-lomakedata @paallystys/tila)
        paakohde (select-keys perustiedot tr/paaluvali-avaimet)
        vuosi 2021 ;; riittää pot2:lle aina
        ;; Kohteiden päällekkyys keskenään validoidaan taulukko tasolla, jotta rivin päivittäminen oikeaksi korjaa
        ;; myös toisilla riveillä olevat validoinnit.
        validoitu (if (= (:tr-numero paakohde) (:tr-numero rivi))
                    (yllapitokohteet-domain/validoi-alikohde paakohde rivi [] (get tr-osien-tiedot (:tr-numero rivi)) vuosi)
                    (yllapitokohteet-domain/validoi-muukohde paakohde rivi [] (get tr-osien-tiedot (:tr-numero rivi)) vuosi))]
    (yllapitokohteet-domain/validoitu-kohde-tekstit (dissoc validoitu :alikohde-paallekkyys :muukohde-paallekkyys) false)))

(defn kohde-toisten-kanssa-paallekkain-validointi
  [alikohde? _ rivi taulukko]
  (let [toiset-alikohteet (keep (fn [[indeksi kohdeosa]]
                                  (when (and (:tr-alkuosa kohdeosa) (:tr-alkuetaisyys kohdeosa)
                                             (:tr-loppuosa kohdeosa) (:tr-loppuetaisyys kohdeosa)
                                             (not= kohdeosa rivi))
                                    kohdeosa))
                                taulukko)
        paallekkyydet (filter #(yllapitokohteet-domain/tr-valit-paallekkain? rivi %)
                              toiset-alikohteet)]
    (yllapitokohteet-domain/validoitu-kohde-tekstit {:alikohde-paallekkyys
                                                     paallekkyydet}
                                                    (not alikohde?))))

(defn paallystekerros
  "Alikohteiden päällystekerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot tr-osien-pituudet ohjauskahvat] :as app}
   {:keys [massat materiaalikoodistot validointi virheet-atom]} kohdeosat-atom]
  (let [voi-muokata? (not= :lukittu (:tila perustiedot))
        ohjauskahva (:paallystekerros ohjauskahvat)]
    [grid/muokkaus-grid
     {:otsikko "Kulutuskerros" :tunniste :kohdeosa-id :rivinumerot? true
      :voi-muokata? voi-muokata? :voi-lisata? false
      :voi-kumota? false
      :muutos #(e! (pot2-tiedot/->Pot2Muokattu))
      :custom-toiminto {:teksti "Lisää toimenpide"
                        :toiminto #(e! (pot2-tiedot/->LisaaPaallysterivi kohdeosat-atom))
                        :opts {:ikoni (ikonit/livicon-plus)
                               :luokka "nappi-toissijainen"}}
      :ohjaus ohjauskahva :validoi-alussa? true
      :virheet virheet-atom
      :piilota-toiminnot? true
      :rivi-validointi (:rivi validointi)
      :taulukko-validointi (:taulukko validointi)
      :tyhja (if (nil? @kohdeosat-atom)
               [ajax-loader "Haetaan kohdeosia..."]
               [yleiset/vihje "Aloita painamalla Lisää toimenpide -painiketta."])}
     [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :tayta-alas? pot2-tiedot/tayta-alas?-fn
       :tyyppi :valinta :valinnat (or (:paallystekerros-toimenpiteet materiaalikoodistot) []) :valinta-arvo ::pot2-domain/koodi
       :valinta-nayta ::pot2-domain/lyhenne :validoi [[:ei-tyhja "Anna arvo"]]
       :leveys (:toimenpide pot2-yhteiset/gridin-leveydet)}
      {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-numero :validoi (:tr-numero validointi)}
      {:otsikko "Ajor." :nimi :tr-ajorata :tyyppi :valinta :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)
       :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
       :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))
       :tasaa :oikea :kokonaisluku? true :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)
       :valinnat pot/+kaistat+ :valinta-arvo :koodi
       :valinta-nayta (fn [rivi]
                        (if rivi
                          (:nimi rivi)
                          "- Valitse kaista -"))
       :tasaa :oikea :kokonaisluku? true :validoi [[:ei-tyhja "Anna arvo"]]}
      {:otsikko "Aosa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-alkuosa :validoi (:tr-alkuosa validointi)}
      {:otsikko "Aet" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-alkuetaisyys :validoi (:tr-alkuetaisyys validointi)}
      {:otsikko "Losa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-loppuosa :validoi (:tr-loppuosa validointi)}
      {:otsikko "Let" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-loppuetaisyys :validoi (:tr-loppuetaisyys validointi)}
      {:otsikko "Pituus" :nimi :pituus :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :tyyppi :positiivinen-numero :tasaa :oikea
       :muokattava? (constantly false)
       :hae (fn [rivi]
              (tr/laske-tien-pituus (into {}
                                          (map (juxt key (comp :pituus val)))
                                          (get tr-osien-pituudet (:tr-numero rivi)))
                                    rivi))}
      {:otsikko "Pääl\u00ADlyste" :nimi :materiaali :leveys (:materiaali pot2-yhteiset/gridin-leveydet) :tayta-alas? pot2-tiedot/tayta-alas?-fn
       :tyyppi :valinta
       :valinnat-fn (fn [rivi]
                      (let [karhinta-toimenpide? (= pot2-domain/+kulutuskerros-toimenpide-karhinta+ (:toimenpide rivi))
                            massa-valinnainen? karhinta-toimenpide?
                            massat (or massat [])]
                        (if massa-valinnainen?
                          (cons {::pot2-domain/massa-id nil :tyhja "ei päällystettä"}
                                massat)
                          massat)))
       :valinta-arvo ::pot2-domain/massa-id
       :linkki-fn (fn [arvo]
                    (e! (pot2-tiedot/->NaytaMateriaalilomake {::pot2-domain/massa-id arvo} true)))
       :linkki-icon (ikonit/livicon-external)
       :valinta-nayta (fn [rivi]
                        (if (empty? massat)
                          [:div.neutraali-tausta "Lisää massa"]
                          (if-let [tyhja (:tyhja rivi)]
                            [:span tyhja]
                            [:div.pot2-paallyste
                             [mk-tiedot/materiaalin-rikastettu-nimi {:tyypit (:massatyypit materiaalikoodistot)
                                                                     :materiaali (pot2-tiedot/rivi->massa-tai-murske rivi {:massat massat})
                                                                     :fmt :komponentti}]])))
       :validoi [[:ei-tyhja-jos-toinen-avain-ei-joukossa :toimenpide [pot2-domain/+kulutuskerros-toimenpide-karhinta+] "Anna arvo"]]}
      {:otsikko "Leveys (m)" :nimi :leveys :tyyppi :positiivinen-numero :tasaa :oikea
       :tayta-alas? pot2-tiedot/tayta-alas?-fn :desimaalien-maara 2
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :validoi [[:ei-tyhja "Anna arvo"]]
       :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 20 2))}
      {:otsikko "Kok.m. (t)" :nimi :kokonaismassamaara :tyyppi :positiivinen-numero :tasaa :oikea
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :validoi [[:ei-tyhja "Anna arvo"]]
       :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 1000000 1))}
      {:otsikko "Pinta-ala (m²)" :nimi :pinta_ala :tyyppi :positiivinen-numero :tasaa :oikea :muokattava? (constantly false)
       :fmt #(fmt/desimaaliluku-opt % 1)
       :hae (fn [rivi]
              (when-let [pituus (tr/laske-tien-pituus (into {}
                                                            (map (juxt key (comp :pituus val)))
                                                            (get tr-osien-pituudet (:tr-numero rivi)))
                                                      rivi)]
                (when (:leveys rivi)
                  (* (:leveys rivi) pituus))))
       :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)}
      {:otsikko "Massa\u00ADmenekki (kg/m\u00B2)" :nimi :massamenekki :tyyppi :positiivinen-numero :tasaa :oikea
       :fmt #(fmt/desimaaliluku-opt % 1) :muokattava? (constantly false)
       :hae (fn [rivi]
              (let [massamaara (:kokonaismassamaara rivi)
                    pinta-ala (:pinta_ala rivi)]
                (when (and massamaara (> pinta-ala 0))
                  (/ (* 1000 massamaara) ;; * 1000, koska kok.massamäärä on tonneja, halutaan kg/m2
                     pinta-ala))))
       :tayta-alas? pot2-tiedot/tayta-alas?-fn :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)}
      {:otsikko "" :nimi :kulutuspaallyste-toiminnot :tyyppi :reagent-komponentti :leveys (:toiminnot pot2-yhteiset/gridin-leveydet)
       :tasaa :keskita :komponentti-args [e! app kirjoitusoikeus? kohdeosat-atom :paallystekerros voi-muokata? ohjauskahva]
       :komponentti pot2-yhteiset/rivin-toiminnot-sarake}]
     kohdeosat-atom]))
