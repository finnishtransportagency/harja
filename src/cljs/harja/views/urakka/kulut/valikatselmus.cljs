(ns harja.views.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.urakka :as urakka]
            [harja.tiedot.urakka.kulut.valikatselmus :as t]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.kulut.yhteiset :as yhteiset]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.ui.validointi :as validointi]
            [harja.ui.komponentti :as komp]))

(def debug-atom (atom {}))

(def +tavoitepalkkio-kerroin+ 0.3)
(def +maksimi-tavoitepalkkio-prosentti+ 0.03)

(defn valikatselmus-otsikko-ja-tiedot [app]
  (let [urakan-nimi (:nimi @nav/valittu-urakka)
        valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        urakan-alkuvuosi (pvm/vuosi (:alkupvm @nav/valittu-urakka))
        hoitokausi-str (pvm/paivamaaran-hoitokausi-str (pvm/hoitokauden-alkupvm valittu-hoitokauden-alkuvuosi))]
    [:<>
     [:h1 "Välikatselmuksen päätökset"]
     [:div.caption urakan-nimi]
     [:div.caption (str (inc (- valittu-hoitokauden-alkuvuosi urakan-alkuvuosi)) ". hoitovuosi (" hoitokausi-str ")")]]))

(defn tavoitehinnan-oikaisut [_ app]
  (let [tallennettu-tila (atom @(:tavoitehinnan-oikaisut-atom app))
        virheet (atom {})]
    (fn [e! {:keys [tavoitehinnan-oikaisut-atom]}]
      [grid/muokkaus-grid
       {:otsikko "Tavoitehinnan oikaisut"
        :tyhja "Ei oikaisuja"
        :voi-kumota? false
        :toimintonappi-fn (fn [rivi muokkaa!]
                            [napit/poista ""
                             #(do
                                (e! (t/->PoistaOikaisu rivi muokkaa!))
                                (reset! tallennettu-tila @tavoitehinnan-oikaisut-atom))
                             {:luokka "napiton-nappi"}])
        :uusi-rivi-nappi-luokka "nappi-ensisijainen"
        :lisaa-rivi "Lisää oikaisu"
        :validoi-uusi-rivi? false
        :on-rivi-blur (fn [oikaisu i]
                        (when-not (or (= @tallennettu-tila @tavoitehinnan-oikaisut-atom) (seq (get @virheet i)) (:koskematon (get @tavoitehinnan-oikaisut-atom i)))
                          (e! (t/->TallennaOikaisu oikaisu i))
                          (reset! tallennettu-tila @tavoitehinnan-oikaisut-atom)))
        :uusi-id (if (empty? (keys @tavoitehinnan-oikaisut-atom))
                   0
                   (inc (apply max (keys @tavoitehinnan-oikaisut-atom))))
        :virheet virheet
        :nayta-virheikoni? false}
       [{:otsikko "Luokka"
         :nimi ::valikatselmus/otsikko
         :tyyppi :valinta
         :valinnat valikatselmus/luokat
         :validoi [[:ei-tyhja "Valitse arvo"]]
         :leveys 2}
        {:otsikko "Selite"
         :nimi ::valikatselmus/selite
         :tyyppi :string
         :validoi [[:ei-tyhja "Täytä arvo"]]
         :leveys 3}
        {:otsikko "Lisäys / Vähennys"
         :nimi :lisays-tai-vahennys
         :hae #(if (> 0 (::valikatselmus/summa %)) :vahennys :lisays)
         :aseta (fn [rivi arvo]
                  ;; Käännetään summa, jos valittu arvo ei täsmää arvon merkkisyyteen.
                  (let [maksu (js/parseFloat (::valikatselmus/summa rivi))
                        rivi (assoc rivi :lisays-tai-vahennys arvo)]
                    (if (or (and (neg? maksu) (= :lisays arvo)) (and (pos? maksu) (= :vahennys arvo)))
                      (update rivi ::valikatselmus/summa -)
                      rivi)))
         :tyyppi :valinta
         :valinnat [:lisays :vahennys]
         :valinta-nayta {:lisays "Lisäys"
                         :vahennys "Vähennys"}
         :leveys 2}
        {:otsikko "Summa"
         :nimi ::valikatselmus/summa
         :tyyppi :numero
         :tasaa :oikea
         :aseta (fn [rivi arvo]
                  (let [vahennys? (= :vahennys (:lisays-tai-vahennys rivi))]
                    (if (and vahennys? (pos? arvo))
                      (assoc rivi ::valikatselmus/summa (- arvo))
                      (assoc rivi ::valikatselmus/summa arvo))))
         :fmt #(if (neg? (js/parseFloat %)) (str (- (js/parseFloat %))) (str %))
         :validoi [[:ei-tyhja "Täytä arvo"]]
         :leveys 2}]
       tavoitehinnan-oikaisut-atom])))

(defn- kaanna-euro-ja-prosentti [vanhat-tiedot uusi-valinta ylitys-tai-alitus]
  (let [vanha-maksu (:maksu vanhat-tiedot)
        vanha-valinta (:euro-vai-prosentti vanhat-tiedot)]
    (as-> vanhat-tiedot tiedot
          (if (and uusi-valinta vanha-valinta (not= uusi-valinta vanha-valinta))
            (assoc tiedot :maksu (if (= :prosentti uusi-valinta)
                                   (* 100 (/ vanha-maksu ylitys-tai-alitus))
                                   (/ (* vanha-maksu ylitys-tai-alitus) 100)))
            ;; Jos valinta ei ole vaihtunut, ei tehdä mitään. Näin käy esim. kun lomakkeen sulkee ja aukaisee uudestaan.
            tiedot)
          (assoc tiedot :euro-vai-prosentti uusi-valinta))))

(defn- maksu-validi? [lomake ylitys-tai-alitus-maara]
  (if (= :prosentti (:euro-vai-prosentti lomake))
    (>= 100 (:maksu lomake))
    (>= ylitys-tai-alitus-maara (:maksu lomake))))

(defn- tavoitepalkkio-maksimi-ylitetty? [lomake tavoitepalkkio oikaistu-tavoitehinta]
  (let [palkkio (:maksu lomake)
        palkkio-prosentteina? (= :prosentti (:euro-vai-prosentti lomake))
        palkkio-euroina (if palkkio-prosentteina?
                          (/ (* palkkio tavoitepalkkio) 100)
                          palkkio)]
    (> palkkio-euroina (* +maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta))))

;; vertailtava-summa on ylityksen, alituksen tai tavoitepalkkion määrä.
(defn paatos-maksu-lomake
  ([e! app paatos-avain vertailtava-summa]
   (paatos-maksu-lomake e! app paatos-avain vertailtava-summa nil))
  ([e! app paatos-avain vertailtava-summa oikaistu-tavoitehinta]
   (let [lomake (paatos-avain app)
         alitus? (= :tavoitehinnan-alitus-lomake paatos-avain)
         maksimi-tavoitepalkkio (min vertailtava-summa (* +maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta))
         maksimi-tavoitepalkki-prosenttina (* 100 (/ maksimi-tavoitepalkkio vertailtava-summa))]
     [:div.maksu-kentat
      [lomake/lomake {:ei-borderia? true
                      :muokkaa! #(e! (t/->PaivitaPaatosLomake % paatos-avain))
                      :kutsu-muokkaa-renderissa? true
                      :tarkkaile-ulkopuolisia-muutoksia? true
                      :validoi-alussa? true}
       [{:nimi :maksu
         :piilota-label? true
         ::lomake/col-luokka "col-md-4 margin-top-16 paatos-maksu"
         :tyyppi :numero
         :vayla-tyyli? true
         :validoi [#(when (not (maksu-validi? lomake vertailtava-summa)) "Maksun määrä ei voi olla yli 100%")
                   #(when (and alitus? (tavoitepalkkio-maksimi-ylitetty? lomake vertailtava-summa oikaistu-tavoitehinta)) "Tavoitepalkkio ei voi ylittää 3% tavoitehinnasta")
                   [:ei-tyhja "Täytä arvo"]]
         :desimaalien-maara 2
         :oletusarvo (if alitus? (/ maksimi-tavoitepalkki-prosenttina 2) 30)}
        {:nimi :euro-vai-prosentti
         :tyyppi :radio-group
         :vaihtoehdot [:prosentti :euro]
         :vayla-tyyli? true
         :nayta-rivina? true
         :piilota-label? true
         ::lomake/col-luokka "col-md-7"
         :aseta #(kaanna-euro-ja-prosentti %1 %2 vertailtava-summa)
         :vaihtoehto-nayta {:prosentti "prosenttia"
                            :euro "euroa"}
         :oletusarvo :prosentti}]
       lomake]])))

(defn tavoitehinnan-ylitys-lomake [e! app toteuma oikaistu-tavoitehinta]
  (let [ylityksen-maara (- toteuma oikaistu-tavoitehinta)
        lomake (:tavoitehinnan-ylitys-lomake app)
        hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        muokattava? (or (not (::valikatselmus/paatoksen-id lomake)) (:muokataan? lomake))
        maksu-prosentteina? (= :prosentti (:euro-vai-prosentti lomake))
        maksu (:maksu lomake)
        urakoitsijan-maksu (if maksu-prosentteina?
                             (/ (* maksu ylityksen-maara) 100)
                             maksu)
        tilaajan-maksu (- ylityksen-maara urakoitsijan-maksu)
        maksu-prosentteina (if maksu-prosentteina?
                             maksu
                             (* 100 (/ maksu ylityksen-maara)))
        paatoksen-tiedot (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                                 ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                 ::valikatselmus/urakoitsijan-maksu urakoitsijan-maksu
                                 ::valikatselmus/tilaajan-maksu tilaajan-maksu
                                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi}
                                (when (::valikatselmus/paatoksen-id lomake)
                                  {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id lomake)}))]
    [:div.paatos
     [:div
      {:class ["paatos-check" (when muokattava? "ei-tehty")]}
      [ikonit/livicon-check]]
     [:div.paatos-sisalto
      [:h3 (str "Tavoitehinnan ylitys " (fmt/desimaaliluku ylityksen-maara))]
      (if muokattava?
        [:<>
         [:p "Urakoitsija maksaa hyvitystä ylityksestä"]
         [paatos-maksu-lomake e! app :tavoitehinnan-ylitys-lomake ylityksen-maara]]
        [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksu-prosentteina) "%"]])
      (when (and urakoitsijan-maksu maksu-prosentteina)
        [:div.osuusrivit
         [:p.osuusrivi "Urakoitsijan osuus " [:strong (fmt/desimaaliluku urakoitsijan-maksu)] "€ (" (fmt/desimaaliluku maksu-prosentteina) "%)"]
         [:p.osuusrivi "Tilaajan osuus " [:strong (fmt/desimaaliluku tilaajan-maksu)] "€ (" (fmt/desimaaliluku (- 100 maksu-prosentteina)) "%)"]])
      (if muokattava?
        [napit/yleinen-ensisijainen "Tallenna päätös"
         #(e! (t/->TallennaPaatos paatoksen-tiedot))
         {:disabled (seq (-> app :tavoitehinnan-ylitys-lomake ::lomake/virheet))}]
        [napit/muokkaa "Muokkaa päätöstä" #(e! (t/->MuokkaaPaatosta :tavoitehinnan-ylitys-lomake)) {:luokka "napiton-nappi"}])]]))

(defn tavoitehinnan-alitus-lomake [e! {:keys [hoitokauden-alkuvuosi tavoitehinnan-alitus-lomake] :as app} toteuma oikaistu-tavoitehinta]
  (let [alituksen-maara (- oikaistu-tavoitehinta toteuma)
        tavoitepalkkio (* +tavoitepalkkio-kerroin+ alituksen-maara)
        ;; Maksimi maksettava tavoitepalkkio, eli jos yli 30% tavoitehinnan alituksesta, yli jäävä osa on pakko siirtää.
        maksimi-tavoitepalkkio (* +maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta)
        tavoitepalkkio-yli-maksimin? (< maksimi-tavoitepalkkio tavoitepalkkio)
        muokattava? (or (not (::valikatselmus/paatoksen-id tavoitehinnan-alitus-lomake)) (:muokataan? tavoitehinnan-alitus-lomake))
        tavoitepalkkion-tyyppi (:tavoitepalkkion-tyyppi tavoitehinnan-alitus-lomake)
        osa-valittu? (= :osa tavoitepalkkion-tyyppi)
        maksu-valittu? (= :maksu tavoitepalkkion-tyyppi)
        siirto-valittu? (= :siirto tavoitepalkkion-tyyppi)
        palkkio-prosentteina? (if osa-valittu? (= :prosentti (:euro-vai-prosentti tavoitehinnan-alitus-lomake)) false)
        maksettava-palkkio (cond
                             osa-valittu? (or (:maksu tavoitehinnan-alitus-lomake) 0)
                             maksu-valittu? (if tavoitepalkkio-yli-maksimin? maksimi-tavoitepalkkio tavoitepalkkio)
                             siirto-valittu? 0)
        maksettava-palkkio-euroina (if palkkio-prosentteina?
                                     (/ (* maksettava-palkkio tavoitepalkkio) 100)
                                     maksettava-palkkio)
        maksettava-palkkio-prosentteina (if palkkio-prosentteina?
                                          maksettava-palkkio
                                          (* 100 (/ maksettava-palkkio tavoitepalkkio)))
        siirto (- tavoitepalkkio maksettava-palkkio-euroina)
        paatoksen-tiedot (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                                 ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-alitus
                                 ::valikatselmus/urakoitsijan-maksu (- maksettava-palkkio-euroina)
                                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                 ::valikatselmus/siirto siirto}
                                (when (::valikatselmus/paatoksen-id tavoitehinnan-alitus-lomake)
                                  {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id tavoitehinnan-alitus-lomake)}))]
    [:<>
     [debug/debug {:alituksen-maara alituksen-maara
                   :toteuma toteuma
                   :palkkio-euroina maksettava-palkkio-euroina
                   :siirto siirto
                   :tavoitehinnan-alitus-lomake tavoitehinnan-alitus-lomake
                   :maksimi-tp maksimi-tavoitepalkkio
                   :paatoksen-tiedot paatoksen-tiedot}]
     [:div.paatos
      [:div
       {:class ["paatos-check" (when muokattava? "ei-tehty")]}
       [ikonit/livicon-check]]
      [:div.paatos-sisalto
       [:h3 (str "Tavoitehinnan alitus " (fmt/desimaaliluku alituksen-maara))]
       (when tavoitepalkkio-yli-maksimin?
         [:div.tavoitepalkkio-ylitys
          [ikonit/harja-icon-status-alert]
          [:span "Tavoitepalkkion maksimimäärä (3% tavoitehinnasta) ylittyy. " [:strong (fmt/desimaaliluku (- tavoitepalkkio maksimi-tavoitepalkkio)) " €"] " siirretään automaattisesti seuraavalle vuodelle alennukseksi."]])
       [:p "Tavoitepalkkion määrä on " [:strong (fmt/desimaaliluku tavoitepalkkio)] " euroa (30%)"]
       (if muokattava?
         [:<>
          [kentat/tee-kentta
           {:nimi :tavoitepalkkion-tyyppi
            :tyyppi :radio-group
            :vaihtoehdot [:maksu :osa :siirto]
            :vayla-tyyli? true
            :piilota-label? true
            ::lomake/col-luokka "col-md-7"
            :vaihtoehto-opts {:osa {:selite "Urakoitsija kirjaa palkkion osalta hyvitysmaksun Harjaan"
                                    :valittu-komponentti [:div.tavoitepalkkio-maksu
                                                          [:h4 "Palkkion osuus"]
                                                          [paatos-maksu-lomake e! app :tavoitehinnan-alitus-lomake tavoitepalkkio oikaistu-tavoitehinta]
                                                          (when maksettava-palkkio-euroina
                                                            [:div.osuusrivit
                                                             [:p.osuusrivi "Maksetaan palkkiona: " [:strong (fmt/desimaaliluku maksettava-palkkio-euroina)] "€ (" (fmt/desimaaliluku maksettava-palkkio-prosentteina) "%)"]
                                                             [:p.osuusrivi "Siirretään seuraavan vuoden alennukseksi: " [:strong (fmt/desimaaliluku siirto)] "€ (" (fmt/desimaaliluku (- 100 maksettava-palkkio-prosentteina)) "%)"]])]}}
            :vaihtoehto-nayta {:maksu "Maksetaan kokonaan palkkiona"
                               :osa "Maksetaan osa palkkiona ja siirretään osa"
                               :siirto "Siirretään kaikki seuraavan vuoden alennukseksi"}
            :oletusarvo :maksu}
           (r/wrap tavoitepalkkion-tyyppi
                   #(e! (t/->PaivitaTavoitepalkkionTyyppi %)))]]
         [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksettava-palkkio-prosentteina) "%"]])
       (if muokattava?
         [napit/yleinen-ensisijainen "Tallenna päätös"
          #(e! (t/->TallennaPaatos paatoksen-tiedot))
          {:disabled (and osa-valittu? (seq (::lomake/virheet tavoitehinnan-alitus-lomake)))}]
         [napit/muokkaa "Muokkaa päätöstä" #(e! (t/->MuokkaaPaatosta :tavoitehinnan-alitus-lomake)) {:luokka "napiton-nappi"}])]]]))

(defn paatokset [e! app]
  (let [hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        hoitokausi-nro (kustannusten-seuranta-tiedot/hoitokauden-jarjestysnumero hoitokauden-alkuvuosi)
        oikaisujen-summa (yhteiset/oikaisujen-summa @(:tavoitehinnan-oikaisut-atom app))
        tavoitehinta (or (kustannusten-seuranta-tiedot/hoitokauden-tavoitehinta hoitokausi-nro app) 0)
        kattohinta (or (kustannusten-seuranta-tiedot/hoitokauden-kattohinta hoitokausi-nro app) 0)
        oikaistu-tavoitehinta (+ oikaisujen-summa tavoitehinta)
        oikaistu-kattohinta (+ oikaisujen-summa kattohinta)
        toteuma (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        alitus? (> oikaistu-tavoitehinta toteuma)
        tavoitehinnan-ylitys? (< oikaistu-tavoitehinta toteuma)
        kattohinnan-ylitys? (< oikaistu-kattohinta toteuma)]
    [:div
     (when tavoitehinnan-ylitys?
       [tavoitehinnan-ylitys-lomake e! app toteuma oikaistu-tavoitehinta])
     (when alitus?
       [tavoitehinnan-alitus-lomake e! app toteuma oikaistu-tavoitehinta])]))

(defn valikatselmus [e! app]
  (komp/luo
    (komp/sisaan (if (nil? (:urakan-paatokset app)) #(e! (t/->HaeUrakanPaatokset (-> @tila/yleiset :urakka :id)))
                                                    #(e! (t/->AlustaPaatosLomakkeet (:urakan-paatokset app) (:hoitokauden-alkuvuosi app)))))
    (fn [e! app]
      [:div.valikatselmus-container
       [napit/takaisin "Takaisin" #(e! (kustannusten-seuranta-tiedot/->SuljeValikatselmusLomake)) {:luokka "napiton-nappi tumma"}]
       [valikatselmus-otsikko-ja-tiedot app]
       [:div.valikatselmus-ja-yhteenveto
        [:div.oikaisut-ja-paatokset
         [tavoitehinnan-oikaisut e! app]
         [paatokset e! app :tavoitehinnan-ylitys-lomake]]
        [:div.yhteenveto-container
         [yhteiset/yhteenveto-laatikko e! app (:kustannukset app) :valikatselmus]]]])))
