(ns harja.views.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.urakka :as urakka]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.urakka.kulut.valikatselmus :as t]
            [harja.tiedot.urakka.lupaukset :as lupaus-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.views.urakka.kulut.yhteiset :as yhteiset]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]))

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
  (let [tallennettu-tila (atom (get-in app [:tavoitehinnan-oikaisut (:hoitokauden-alkuvuosi app)]))
        virheet (atom {})]
    (fn [e! {:keys [tavoitehinnan-oikaisut hoitokauden-alkuvuosi] :as app}]
      (let [oikaisut-atom (reagent.core/cursor tila/tavoitehinnan-oikaisut [(:hoitokauden-alkuvuosi app)])
            paatoksia? (not (empty? (:urakan-paatokset app)))
            hoitokauden-oikaisut (get tavoitehinnan-oikaisut hoitokauden-alkuvuosi)]
        [:div
         [grid/muokkaus-grid
          {:otsikko "Tavoitehinnan oikaisut"
           :tyhja "Ei oikaisuja"
           :voi-kumota? false
           :toimintonappi-fn (fn [rivi _muokkaa! id]
                               [napit/poista ""
                                #(do
                                   (e! (t/->PoistaOikaisu rivi id))
                                   (reset! tallennettu-tila hoitokauden-oikaisut))
                                {:luokka "napiton-nappi"}])
           :uusi-rivi-nappi-luokka "nappi-ensisijainen"
           :lisaa-rivi "Lisää oikaisu"
           :validoi-uusi-rivi? false
           :on-rivi-blur (fn [oikaisu i]
                           (when-not (or (= @tallennettu-tila hoitokauden-oikaisut)
                                         (seq (get @virheet i))
                                         (:koskematon (get hoitokauden-oikaisut i)))
                             (e! (t/->TallennaOikaisu oikaisu i))
                             (reset! tallennettu-tila hoitokauden-oikaisut)))
           :uusi-id (if (empty? (keys hoitokauden-oikaisut))
                      0
                      (inc (apply max (keys hoitokauden-oikaisut))))
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
            :hae #(if (neg? (::valikatselmus/summa %)) :vahennys :lisays)
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
          oikaisut-atom]

         (when paatoksia?
           ;; TODO: Tarkista, että päätökset haetaan oikein kun ne tallennetaan
           [:div.oikaisu-paatos-varoitus
            [ikonit/harja-icon-status-alert]
            [:span "Hinnan oikaisun jälkeen joudut tallentamaan päätökset uudestaan"]])]))))

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
    (> palkkio-euroina (* t/+maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta))))

;; vertailtava-summa on ylityksen tai tavoitepalkkion määrä.
(defn paatos-maksu-lomake
  ([e! app paatos-avain vertailtava-summa]
   (paatos-maksu-lomake e! app paatos-avain vertailtava-summa nil))
  ([e! app paatos-avain vertailtava-summa oikaistu-tavoitehinta]
   (let [lomake (paatos-avain app)
         alitus? (= :tavoitehinnan-alitus-lomake paatos-avain)
         maksimi-tavoitepalkkio (min vertailtava-summa (* t/+maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta))
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
        tavoitepalkkio (* t/+tavoitepalkkio-kerroin+ alituksen-maara)
        ;; Maksimi maksettava tavoitepalkkio, eli jos yli 30% tavoitehinnan alituksesta, yli jäävä osa on pakko siirtää.
        maksimi-tavoitepalkkio (* t/+maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta)
        tavoitepalkkio-yli-maksimin? (< maksimi-tavoitepalkkio tavoitepalkkio)
        muokattava? (or (not (::valikatselmus/paatoksen-id tavoitehinnan-alitus-lomake)) (:muokataan? tavoitehinnan-alitus-lomake))
        tavoitepalkkion-tyyppi (:tavoitepalkkion-tyyppi tavoitehinnan-alitus-lomake)
        osa-valittu? (= :osa tavoitepalkkion-tyyppi)
        maksu-valittu? (= :maksu tavoitepalkkion-tyyppi)
        siirto-valittu? (= :siirto tavoitepalkkion-tyyppi)
        palkkio-prosentteina? (if osa-valittu? (= :prosentti (:euro-vai-prosentti tavoitehinnan-alitus-lomake)) false)
        viimeinen-hoitokausi? (>= hoitokauden-alkuvuosi (dec (pvm/vuosi (:loppupvm @nav/valittu-urakka))))
        maksettava-palkkio (cond
                             ;; Viimeisenä vuotena tavoitepalkkio voi ylittää 3% tavoitehinnasta, koska ei voida siirtää.
                             viimeinen-hoitokausi? tavoitepalkkio
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
                                 ::valikatselmus/siirto (- siirto)}
                                (when (::valikatselmus/paatoksen-id tavoitehinnan-alitus-lomake)
                                  {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id tavoitehinnan-alitus-lomake)}))]
    [:<>
     [:div.paatos
      [:div
       {:class ["paatos-check" (when muokattava? "ei-tehty")]}
       [ikonit/livicon-check]]
      [:div.paatos-sisalto
       [:h3 (str "Tavoitehinnan alitus " (fmt/desimaaliluku alituksen-maara))]
       (if-not viimeinen-hoitokausi?
         [:<>
          (when tavoitepalkkio-yli-maksimin?
            [:div.tavoitepalkkio-ylitys
             [ikonit/harja-icon-status-alert]
             [:span "Tavoitepalkkion maksimimäärä (3% tavoitehinnasta) ylittyy. " [:strong (fmt/desimaaliluku (- tavoitepalkkio maksimi-tavoitepalkkio)) " €"] " siirretään automaattisesti seuraavalle vuodelle alennukseksi."]])
          [:p "Tavoitepalkkion määrä on " [:strong (fmt/desimaaliluku tavoitepalkkio)] " euroa (30%)"]
          (when muokattava?
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
                                                                [:p.osuusrivi "Siirretään seuraavan vuoden alennukseksi: " [:strong (fmt/desimaaliluku siirto)] "€ (" (fmt/desimaaliluku (- 100 maksettava-palkkio-prosentteina)) "%)"]])]}
                                 :maksu {:selite "Urakoitsija kirjaa hyvitysmaksun Harjaan"}}
               :vaihtoehto-nayta {:maksu "Maksetaan kokonaan palkkiona"
                                  :osa "Maksetaan osa palkkiona ja siirretään osa"
                                  :siirto "Siirretään kaikki seuraavan vuoden alennukseksi"}
               :oletusarvo :maksu}
              (r/wrap tavoitepalkkion-tyyppi
                      #(e! (t/->PaivitaTavoitepalkkionTyyppi %)))]])]
         [:p.maksurivi "Urakoitsijalle maksetaan tavoitepalkkiota " [:strong (fmt/desimaaliluku maksettava-palkkio-euroina) "€"]])

       (if muokattava?
         [napit/yleinen-ensisijainen "Tallenna päätös"
          #(e! (t/->TallennaPaatos paatoksen-tiedot))
          {:disabled (and osa-valittu? (seq (::lomake/virheet tavoitehinnan-alitus-lomake)))}]
         [napit/muokkaa "Muokkaa päätöstä" #(e! (t/->MuokkaaPaatosta :tavoitehinnan-alitus-lomake)) {:luokka "napiton-nappi"}])]]]))

(defn kattohinnan-ylitys-siirto [e! ylityksen-maara {:keys [siirto] :as kattohinnan-ylitys-lomake}]
  [:div.kattohinnan-ylitys-maksu
   [:p "Seuraavalle vuodelle siirretään:"]
   [kentat/tee-otsikollinen-kentta {:otsikko "Siirrettävä summa"
                                    :otsikon-luokka "caption"
                                    :luokka ""
                                    :kentta-params {:otsikko "Siirrettävä summa"
                                                    :tyyppi :positiivinen-numero
                                                    :desimaalien-maara 2
                                                    :piilota-yksikko-otsikossa? true
                                                    :nimi :siirto
                                                    :tasaa :oikea
                                                    :validoi [#(when (< % ylityksen-maara) "Siirrettävä summa ei voi olla yli 100%")
                                                              [:ei-tyhja "Täytä arvo"]]
                                                    :pakollinen? true
                                                    :vayla-tyyli? true
                                                    :yksikko "€"}
                                    :arvo-atom (r/wrap siirto
                                                       #(e! (t/->PaivitaPaatosLomake (assoc kattohinnan-ylitys-lomake :siirto %) :kattohinnan-ylitys-lomake)))}]])

(defn kattohinnan-ylitys-lomake [e! {:keys [hoitokauden-alkuvuosi kattohinnan-ylitys-lomake] :as app} toteuma oikaistu-kattohinta]
  (let [ylityksen-maara (- toteuma oikaistu-kattohinta)
        muokattava? (or (not (::valikatselmus/paatoksen-id kattohinnan-ylitys-lomake)) (:muokataan? kattohinnan-ylitys-lomake))
        maksun-tyyppi (:maksun-tyyppi kattohinnan-ylitys-lomake)
        osa-valittu? (= :osa maksun-tyyppi)
        maksu-valittu? (= :maksu maksun-tyyppi)
        siirto-valittu? (= :siirto maksun-tyyppi)
        viimeinen-hoitokausi? (>= hoitokauden-alkuvuosi (dec (pvm/vuosi (:loppupvm @nav/valittu-urakka))))
        siirto (cond
                 viimeinen-hoitokausi? 0
                 osa-valittu? (:siirto kattohinnan-ylitys-lomake)
                 maksu-valittu? 0
                 siirto-valittu? ylityksen-maara
                 :else 0)
        maksettava-summa (cond
                           viimeinen-hoitokausi? ylityksen-maara
                           osa-valittu? (- ylityksen-maara (:siirto kattohinnan-ylitys-lomake))
                           maksu-valittu? ylityksen-maara
                           siirto-valittu? 0
                           :else 0)
        maksettava-summa-prosenttina (* 100 (/ maksettava-summa ylityksen-maara))
        paatoksen-tiedot (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                                 ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                 ::valikatselmus/urakoitsijan-maksu maksettava-summa
                                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                 ::valikatselmus/siirto siirto}
                                (when (::valikatselmus/paatoksen-id kattohinnan-ylitys-lomake)
                                  {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id kattohinnan-ylitys-lomake)}))]
    [:<>
     [:div.paatos
      [:div
       {:class ["paatos-check" (when muokattava? "ei-tehty")]}
       [ikonit/livicon-check]]
      [:div.paatos-sisalto
       [:h3 (str "Kattohinnan ylitys " (fmt/desimaaliluku ylityksen-maara))]
       (if-not viimeinen-hoitokausi?
         [:<>
          [:<>
           (when muokattava?
             [kentat/tee-kentta
              {:nimi :maksun-tyyppi
               :tyyppi :radio-group
               :vaihtoehdot [:maksu :siirto :osa]
               :vayla-tyyli? true
               :piilota-label? true
               ::lomake/col-luokka "col-md-7"
               :vaihtoehto-opts {:osa
                                 {:valittu-komponentti [kattohinnan-ylitys-siirto e! ylityksen-maara kattohinnan-ylitys-lomake]}}
               :vaihtoehto-nayta {:maksu [:p "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku ylityksen-maara) "€ "] "(100 %)"]
                                  :siirto [:p "Ylitys " [:strong (fmt/desimaaliluku ylityksen-maara) "€ "] "siirretään seuraavan vuoden hankintakustannuksiin"]
                                  :osa "Osa siirretään ja osa maksetaan"}
               :oletusarvo :maksu}
              (r/wrap maksun-tyyppi
                      #(e! (t/->PaivitaMaksunTyyppi %)))])
           (if siirto-valittu?
             [:p.maksurivi "Siirretään ensi vuoden kustannuksiksi " [:strong (fmt/desimaaliluku siirto) " €"]]
             [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksettava-summa) " €"] " (" (fmt/desimaaliluku maksettava-summa-prosenttina) " %)"])]]
         [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksettava-summa) "€"]])

       (if muokattava?
         [napit/yleinen-ensisijainen "Tallenna päätös"
          #(e! (t/->TallennaPaatos paatoksen-tiedot))
          {:disabled (and osa-valittu? (seq (::lomake/virheet kattohinnan-ylitys-lomake)))}]
         [napit/muokkaa "Muokkaa päätöstä" #(e! (t/->MuokkaaPaatosta :kattohinnan-ylitys-lomake)) {:luokka "napiton-nappi"}])]]]))

(defn lupaus-lomake [e! app]
  (let [yhteenveto (:yhteenveto app)
        hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        paatos-tehty? (or (= :katselmoitu-toteuma (:ennusteen-tila yhteenveto)) false)
        lupaus-bonus (get-in app [:yhteenveto :bonus-tai-sanktio :bonus])
        lupaus-sanktio (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio])
        lupauksen-tyyppi-teksti (if lupaus-bonus
                                  "bonusta "
                                  "sanktiota ")
        urakoitsija-teksti (if (= :ennuste (:ennusteen-tila yhteenveto))
                             "Ennusteen mukaan urakoitsija olisi saamassa "
                             "Urakoitsija saa ")
        urakoitsijan-piste-teksti (if (= :ennuste (:ennusteen-tila yhteenveto))
                                    "Urakoitsija olisi saamassa "
                                    "Urakoitsija sai ")
        maksetaan-teksti (if lupaus-bonus
                           "Maksetaan urakoitsijalle "
                           "Urakoitsija maksaa sanktiota ")
        pisteet (get-in app [:yhteenveto :pisteet :toteuma])
        ennuste-pisteet (get-in app [:yhteenveto :pisteet :ennuste])
        sitoutumis-pisteet (get-in app [:lupaus-sitoutuminen :pisteet])
        paatoksen-tiedot (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                                 ::valikatselmus/tyyppi (if lupaus-bonus ::valikatselmus/lupaus-bonus ::valikatselmus/lupaus-sanktio)
                                 ::valikatselmus/urakoitsijan-maksu (when lupaus-sanktio lupaus-sanktio)
                                 ::valikatselmus/tilaajan-maksu (when lupaus-bonus lupaus-bonus)
                                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                 ::valikatselmus/siirto false}
                                #_ (when (::valikatselmus/paatoksen-id kattohinnan-ylitys-lomake)
                                  {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id kattohinnan-ylitys-lomake)}))]
    [:<>
     [:div.paatos
      [:div
       {:class ["paatos-check" (when-not paatos-tehty? "ei-tehty")]}
       [ikonit/livicon-check]]
      [:div.paatos-sisalto
       [:h3 (str "Lupaukset: " urakoitsija-teksti " " lupauksen-tyyppi-teksti " " (fmt/desimaaliluku lupaus-bonus) "€ luvatun pistemäärän ylittämisestä.")]
       [:p urakoitsijan-piste-teksti (if pisteet
                                       pisteet
                                       ennuste-pisteet) " ja lupasi " sitoutumis-pisteet " pistettä."]
        (cond
          (= :alustava-toteuma (:ennusteen-tila yhteenveto))
          [:div (str maksetaan-teksti lupauksen-tyyppi-teksti (fmt/desimaaliluku (if lupaus-bonus
                                                                                             lupaus-bonus
                                                                                             lupaus-sanktio)) "€ (100%)")]
          (= :katselmoitu-toteuma (:ennusteen-tila yhteenveto))
          [:div.flex-row
           [:div {:style {:flex-grow 1}}
            [kentat/tee-kentta
             {:nimi :lupaus
              :tyyppi :checkbox
              :vayla-tyyli? true
              :piilota-label? true
              :disabled? true
              :disabloi? (constantly true)}
             (r/atom true)]]
           [:div {:style {:flex-grow 10}}
            (str maksetaan-teksti lupauksen-tyyppi-teksti (fmt/desimaaliluku (if lupaus-bonus
                                                                               lupaus-bonus
                                                                               lupaus-sanktio)) "€ (100%)")]]
          :else
          [:div "Lupaukset ovat vasta ennusteena, joten päätöstä ei voi vielä tehdä"])

       ;; Lupausten päätöstä ei voi muokata. Sen voi vain tehdä
       (when
         (and (not paatos-tehty?) (= :alustava-toteuma (:ennusteen-tila yhteenveto)))
         [napit/yleinen-ensisijainen "Tallenna päätös"
          #(e! (t/->TallennaPaatos paatoksen-tiedot))])]]]))

(defn paatokset [e! app]
  (let [hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        hoitokausi-nro (urakka-tiedot/hoitokauden-jarjestysnumero hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        oikaisujen-summa (t-yhteiset/oikaisujen-summa (:tavoitehinnan-oikaisut app) hoitokauden-alkuvuosi)
        tavoitehinta (or (kustannusten-seuranta-tiedot/hoitokauden-tavoitehinta hoitokausi-nro app) 0)
        kattohinta (or (kustannusten-seuranta-tiedot/hoitokauden-kattohinta hoitokausi-nro app) 0)
        oikaistu-tavoitehinta (+ oikaisujen-summa tavoitehinta)
        oikaistu-kattohinta (+ oikaisujen-summa kattohinta)
        toteuma (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        alitus? (> oikaistu-tavoitehinta toteuma)
        tavoitehinnan-ylitys? (< oikaistu-tavoitehinta toteuma)
        kattohinnan-ylitys? (< oikaistu-kattohinta toteuma)
        lupaus? (or (get-in app [:yhteenveto :bonus-tai-sanktio]) false)]
    [:div
     (when tavoitehinnan-ylitys?
       [tavoitehinnan-ylitys-lomake e! app toteuma oikaistu-tavoitehinta])
     (when kattohinnan-ylitys?
       [kattohinnan-ylitys-lomake e! app toteuma oikaistu-kattohinta])
     (when alitus?
       [tavoitehinnan-alitus-lomake e! app toteuma oikaistu-tavoitehinta])
     (when lupaus?
       [lupaus-lomake e! app])]))

(defn valikatselmus [e! app]
  (komp/luo
    (komp/sisaan #(do
                   (e! (lupaus-tiedot/->HaeUrakanLupaustiedot (:urakka @tila/yleiset)))
                   (if (nil? (:urakan-paatokset app))
                     (e! (t/->HaeUrakanPaatokset (-> @tila/yleiset :urakka :id)))
                     (e! (t/->AlustaPaatosLomakkeet (:urakan-paatokset app) (:hoitokauden-alkuvuosi app))))))
    (fn [e! app]
      [:div.valikatselmus-container
       [harja.ui.debug/debug app]
       [napit/takaisin "Takaisin" #(e! (kustannusten-seuranta-tiedot/->SuljeValikatselmusLomake)) {:luokka "napiton-nappi tumma"}]
       [valikatselmus-otsikko-ja-tiedot app]
       [:div.valikatselmus-ja-yhteenveto
        [:div.oikaisut-ja-paatokset
         [tavoitehinnan-oikaisut e! app]
         [paatokset e! app :tavoitehinnan-ylitys-lomake]]
        [:div.yhteenveto-container
         [yhteiset/yhteenveto-laatikko e! app (:kustannukset app) :valikatselmus]]]])))
