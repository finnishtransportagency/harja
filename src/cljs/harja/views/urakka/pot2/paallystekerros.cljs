(ns harja.views.urakka.pot2.paallystekerros
  "POT2-lomakkeen päällystekerros"
  (:require
   [reagent.core :refer [atom] :as r]
   [harja.domain.paallystysilmoitus :as pot]
   [harja.domain.pot2 :as pot2-domain]
   [harja.domain.tierekisteri :as tr]
   [harja.domain.yllapitokohde :as yllapitokohteet-domain]
   [harja.ui.grid.protokollat :as grid-protokollat]
   [harja.domain.paikkaus :as paikaus]
   [harja.ui.grid :as grid]
   [harja.ui.ikonit :as ikonit]
   [harja.ui.yleiset :refer [ajax-loader]]
   [harja.tiedot.urakka.paallystys :as paallystys]
   [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as pot2-yhteiset]
   [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
   [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
   [harja.ui.yleiset :as yleiset]
   [harja.validointi :as v]
   [harja.fmt :as fmt]
   [harja.domain.paikkaus :as paikkaus]))


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
                                    ;; Lisää muihin alikohteisiin taulukon riviin viittava indeksiluku, jotta siihen
                                    ;; voidaan viitata validoinnin virheviestissä.
                                    (assoc kohdeosa
                                      :rivi-indeksi indeksi)))
                                taulukko)
        paallekkyydet (filter #(yllapitokohteet-domain/tr-valit-paallekkain? rivi %)
                              toiset-alikohteet)]
    (yllapitokohteet-domain/validoitu-kohde-tekstit {:alikohde-paallekkyys
                                                     paallekkyydet}
                                                    (not alikohde?))))

(defn paallystekerros
  "Alikohteiden päällystekerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot tr-osien-pituudet ohjauskahvat] :as app}
   {:keys [massat murskeet materiaalikoodistot validointi virheet-atom varoitukset-atom]} kohdeosat-atom]
  (let [hyppyjen-maara (get-in @kohdeosat-atom [1 :hyppyjen-maara])
        alkup-jarjestys (atom @kohdeosat-atom)
        lomaketta-muokattu? (boolean (:kulutuskerros-muokattu? (e! (pot2-tiedot/->KulutuskerrosMuokattu nil))))
        alert-ok-teksti "Kulutuskerros on yhtenäinen (ei hyppyjä)"
        alert-teksti (str "Kulutuskerros ei ole yhtenäinen " (cond
                                                               (> hyppyjen-maara 1)
                                                               (str "(" hyppyjen-maara " hyppyä)")
                                                               :else
                                                               (str "(" hyppyjen-maara " hyppy)")))
        custom-yla-panel (if-not lomaketta-muokattu?
                           (if (> hyppyjen-maara 0)
                             [:div.kulutus-hyppy-info.vahvistamaton
                              [:div.kulutus-hyppy-ikoni-alert (ikonit/alert-svg)]
                              [:div alert-teksti]]

                             [:div.kulutus-hyppy-info
                              [:div.kulutus-hyppy-ikoni-ok (ikonit/harja-icon-status-completed)]
                              [:div alert-ok-teksti]])
                           nil)
        voi-muokata? (not= :lukittu (:tila perustiedot))
        ohjauskahva (:paallystekerros ohjauskahvat)]
    [:div
     [grid/muokkaus-grid
      {:otsikko "Kulutuskerros" :tunniste :kohdeosa-id :rivinumerot? true
       :voi-muokata? voi-muokata? :voi-lisata? false
       :voi-kumota? false
       :custom-yla-panel custom-yla-panel
       :muutos (fn [g]
                 ;; Koska tätä kutsutaan myös sorttauksen yhteydessä, täytyy tarkistaa erillisellä funktiolla onko rivjeä muokattu
                 (let [uusi-jarjestys (grid-protokollat/hae-muokkaustila g)
                       riveja-muokattu? (fn [i data]
                                          ;; Onko käyttäjä muokannut lomaketta, käy läpi vanhan järjestyksen rivit, vertaa niitä uusiin riveihin
                                          ;; Kutsutaan kun sortataan rivejä, ei kutsuta enää sen jälkeen jos käyttäjä muokannut lomaketta
                                          (let [rivi (-> data (nth i nil) (nth 1 nil))
                                                fn-data? (fn [rivi data i]
                                                          (and
                                                            (some? rivi)
                                                            (> (count data) (dec i))))
                                                fn-etsi-vanha (fn [i data etsi fn-data?]
                                                                (let [rivi (-> data (nth i nil) (nth 1 nil))]
                                                                  (if (and rivi etsi
                                                                        (= (:kohdeosa-id rivi) (:kohdeosa-id etsi)))
                                                                    rivi
                                                                    (if (fn-data? rivi data i)
                                                                      (recur (inc i) data etsi fn-data?)
                                                                      nil))))
                                                vanha-rivi (fn-etsi-vanha 0 (vec uusi-jarjestys) rivi fn-data?)
                                                fn-vertaa-arvo (fn [vanha uusi avain]
                                                                 (and vanha uusi
                                                                   (= (avain vanha) (avain uusi))))]
                                            (when (fn-data? rivi data i)
                                              ;; Verrataan relevantteja tietoja mitä käyttäjä voi muokata 
                                              (if (and
                                                    (fn-vertaa-arvo vanha-rivi rivi :toimenpide)
                                                    (fn-vertaa-arvo vanha-rivi rivi :tr-kaista)
                                                    (fn-vertaa-arvo vanha-rivi rivi :tr-ajorata)
                                                    (fn-vertaa-arvo vanha-rivi rivi :tr-loppuosa)
                                                    (fn-vertaa-arvo vanha-rivi rivi :tr-alkuosa)
                                                    (fn-vertaa-arvo vanha-rivi rivi :tr-loppuetaisyys)
                                                    (fn-vertaa-arvo vanha-rivi rivi :materiaali)
                                                    (fn-vertaa-arvo vanha-rivi rivi :tr-alkuetaisyys)
                                                    (fn-vertaa-arvo vanha-rivi rivi :tr-numero))
                                                (recur (inc i) data)
                                                true))))]
                   (when-not lomaketta-muokattu?
                     (when (riveja-muokattu? 0 (vec @alkup-jarjestys))
                       (e! (pot2-tiedot/->KulutuskerrosMuokattu true)))))

                 (reset! alkup-jarjestys @kohdeosat-atom)
                 (e! (pot2-tiedot/->Pot2Muokattu)))
      ;; TODO: Digiroad-kaistojen haku disabloitu, kunnes Digiroad-rajapinnan käyttö ja kaista-aineiston hyödyntäminen
      ;;       on suunniteltu kuntoon validointia ajatellen
      ;;on-rivi-blur (fn [rivi]
      ;;                 (let [{:keys [tr-ajorata]} rivi]
      ;;                   (e! (paallystys/->HaeKaistat
      ;;                         (select-keys rivi tr/paaluvali-avaimet)
      ;;                         tr-ajorata)))
       #_#_:on-rivi-blur on-rivi-blur
       :custom-toiminto {:teksti "Lisää toimenpide"
                         :toiminto #(e! (pot2-tiedot/->LisaaPaallysterivi kohdeosat-atom))
                         :opts {:ikoni (ikonit/livicon-plus)
                                :luokka "nappi-toissijainen"}}
       :ohjaus ohjauskahva :validoi-alussa? true
       :virheet virheet-atom
       :varoitukset varoitukset-atom
       :piilota-toiminnot? true
       ;; Varoitetaan validointivirheistä, mutta ei estetä tallentamista.
       ;; Backendin puolella suoritetaan validointi, kun lomake merkitetään tarkastettavaksi ja tallennetaan.
       :rivi-varoitus (:rivi validointi)
       :taulukko-varoitus (:taulukko validointi)
       :tyhja (if (nil? @kohdeosat-atom)
                [ajax-loader "Haetaan kohdeosia..."]
                [yleiset/vihje "Aloita painamalla Lisää toimenpide -painiketta."])}
      [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :tayta-alas? pot2-tiedot/tayta-alas?-fn
        :tyyppi :valinta :valinnat (or (:paallystekerros-toimenpiteet materiaalikoodistot) []) :valinta-arvo ::pot2-domain/koodi
        :valinta-nayta ::pot2-domain/lyhenne :validoi [[:ei-tyhja "Anna arvo"]]
        :leveys (:toimenpide pot2-yhteiset/gridin-leveydet)
        :sarake-sort {:fn (fn []
                            (reset! pot2-tiedot/valittu-paallystekerros-sort :toimenpide)
                            (pot2-tiedot/jarjesta-ja-indeksoi-atomin-rivit
                              kohdeosat-atom
                              (fn [rivi]
                                (pot2-tiedot/jarjesta-valitulla-sort-funktiolla @pot2-tiedot/valittu-paallystekerros-sort
                                  {:massat massat
                                   :murskeet murskeet
                                   :materiaalikoodistot materiaalikoodistot}
                                  rivi)))
                            (when ohjauskahva
                              (grid/validoi-grid ohjauskahva)))
                      :luokka (when (= @pot2-tiedot/valittu-paallystekerros-sort :toimenpide) "valittu-sort")}}
       {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-numero :validoi (:tr-numero validointi)
        :sarake-sort {:fn (fn []
                            (reset! pot2-tiedot/valittu-paallystekerros-sort :tieosoite)
                            (pot2-tiedot/jarjesta-ja-indeksoi-atomin-rivit
                              kohdeosat-atom
                              (fn [rivi]
                                (pot2-tiedot/jarjesta-valitulla-sort-funktiolla @pot2-tiedot/valittu-paallystekerros-sort
                                  {:massat massat
                                   :murskeet murskeet
                                   :materiaalikoodistot materiaalikoodistot}
                                  rivi)))
                            (when ohjauskahva
                              (grid/validoi-grid ohjauskahva)))
                      :luokka (when (= @pot2-tiedot/valittu-paallystekerros-sort :tieosoite) "valittu-sort")}}
       {:otsikko "Ajor." :nimi :tr-ajorata :tyyppi :valinta :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)
        :alasveto-luokka "kavenna-jos-kapea"
        :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
        :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))
        :kokonaisluku? true :validoi [[:ei-tyhja "Anna arvo"]]}
       {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)
        :alasveto-luokka "kavenna-jos-kapea"
        :valinnat pot/+kaistat+ :valinta-arvo :koodi
        :valinta-nayta (fn [rivi]
                         (if rivi
                           (:nimi rivi)
                           "- Valitse kaista -"))
        :sarake-sort {:fn (fn []
                            (reset! pot2-tiedot/valittu-paallystekerros-sort :kaista)
                            (pot2-tiedot/jarjesta-ja-indeksoi-atomin-rivit
                              kohdeosat-atom
                              (fn [rivi]
                                (pot2-tiedot/jarjesta-valitulla-sort-funktiolla @pot2-tiedot/valittu-paallystekerros-sort
                                  {:massat massat
                                   :murskeet murskeet
                                   :materiaalikoodistot materiaalikoodistot}
                                  rivi)))
                            (when ohjauskahva
                              (grid/validoi-grid ohjauskahva)))
                      :luokka (when (= @pot2-tiedot/valittu-paallystekerros-sort :kaista) "valittu-sort")}
        :kokonaisluku? true :validoi [[:ei-tyhja "Anna arvo"]]}
       {:otsikko "Aosa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-alkuosa :validoi (:tr-alkuosa validointi)}
       {:otsikko "Aet" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-alkuetaisyys :validoi (:tr-alkuetaisyys validointi)
        :korosta-sarake (fn []
                          (if (boolean (:kulutuskerros-muokattu? (e! (pot2-tiedot/->KulutuskerrosMuokattu nil))))
                            false :tr-korosta-aet?))}
       {:otsikko "Losa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-loppuosa :validoi (:tr-loppuosa validointi)}
       {:otsikko "Let" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys (:perusleveys pot2-yhteiset/gridin-leveydet) :nimi :tr-loppuetaisyys :validoi (:tr-loppuetaisyys validointi)
        :korosta-sarake (fn []
                          (if (boolean (:kulutuskerros-muokattu? (e! (pot2-tiedot/->KulutuskerrosMuokattu nil))))
                            false
                            :tr-korosta-let?))}
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
               (paikkaus/massamaara-ja-pinta-ala->massamenekki
                 (:kokonaismassamaara rivi)
                 (:pinta_ala rivi)))
        :tayta-alas? pot2-tiedot/tayta-alas?-fn :leveys (:perusleveys pot2-yhteiset/gridin-leveydet)}
       {:otsikko "" :nimi :kulutuspaallyste-toiminnot :tyyppi :reagent-komponentti :leveys (:toiminnot pot2-yhteiset/gridin-leveydet)
        :tasaa :keskita :komponentti-args [e! app kirjoitusoikeus? kohdeosat-atom :paallystekerros voi-muokata? ohjauskahva]
        :komponentti pot2-yhteiset/rivin-toiminnot-sarake}]
      kohdeosat-atom]

     (let [kokpituus (reduce (fn [acc data]
                               (when-let [pituus (tr/laske-tien-pituus (into {}
                                                                         (map (juxt key (comp :pituus val)))
                                                                         (get tr-osien-pituudet (:tr-numero (second data))))
                                                   (second data))]
                                 (+ acc pituus)))
                       0
                       @kohdeosat-atom)]
       [:div.kulutus-pituus-yhteensa (str "Pituus yhteensä: " kokpituus " m")])]))
