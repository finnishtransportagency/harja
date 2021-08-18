(ns harja.views.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
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
                        (let [vanha (get @tallennettu-tila i)
                              uusi (get @tavoitehinnan-oikaisut-atom i)
                              ;; Jos lisays-tai-vahennys-saraketta on muutettu (mutta summaa ei), käännetään summan merkkisyys
                              oikaisu (if (and (not= (:lisays-tai-vahennys vanha)
                                                     (:lisays-tai-vahennys uusi))
                                               (= (::valikatselmus/summa vanha)
                                                  (::valikatselmus/summa uusi)))
                                        (update oikaisu ::valikatselmus/summa -)
                                        oikaisu)]
                          (swap! tavoitehinnan-oikaisut-atom #(assoc % i oikaisu))
                          (when-not (or (= @tallennettu-tila @tavoitehinnan-oikaisut-atom) (seq (get @virheet i)) (:koskematon (get @tavoitehinnan-oikaisut-atom i)))
                            (e! (t/->TallennaOikaisu oikaisu i))
                            (reset! tallennettu-tila @tavoitehinnan-oikaisut-atom))))
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
         :hae #(if (> 0 (::valikatselmus/summa %)) "Vähennys" "Lisäys")
         :tyyppi :valinta
         :valinnat ["Lisäys" "Vähennys"]
         :leveys 2}
        {:otsikko "Summa"
         :nimi ::valikatselmus/summa
         :tyyppi :numero
         :tasaa :oikea
         :validoi [[:ei-tyhja "Täytä arvo"]]
         :leveys 2}]
       tavoitehinnan-oikaisut-atom])))

(defn- kaanna-euro-ja-prosentti [vanhat-tiedot uusi-valinta ylitys-tai-alitus]
  (let [vanha-maksu (:maksu vanhat-tiedot)]
    (as-> vanhat-tiedot tiedot
          (if (not= uusi-valinta (:euro-vai-prosentti vanhat-tiedot))
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

(defn paatos-maksu-lomake [e! app paatos-avain ylitys-tai-alitus-maara]
  (let [lomake (paatos-avain app)]
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
        :validoi [#(when (not (maksu-validi? lomake ylitys-tai-alitus-maara)) "Maksun määrä ei voi olla yli 100%")
                  [:ei-tyhja "Täytä arvo"]]
        :desimaalien-maara 2
        :oletusarvo 30}
       {:nimi :euro-vai-prosentti
        :tyyppi :radio-group
        :vaihtoehdot [:prosentti :euro]
        :vayla-tyyli? true
        :nayta-rivina? true
        :piilota-label? true
        ::lomake/col-luokka "col-md-7"
        :aseta #(kaanna-euro-ja-prosentti %1 %2 ylitys-tai-alitus-maara)
        :vaihtoehto-nayta {:prosentti "prosenttia"
                           :euro "euroa"}
        :oletusarvo :prosentti}]
      lomake]]))

(defn tavoitehinnan-ylitys-lomake [e! app toteuma oikaistu-tavoitehinta]
  (fn [e! app toteuma oikaistu-tavoitehinta]
    (let [ylityksen-maara (- toteuma oikaistu-tavoitehinta)
          lomake (:tavoitehinnan-ylitys-lomake app)
          muokattava? (or (not (:tallennettu? lomake)) (:muokataan? lomake))
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
                                   ::valikatselmus/tilaajan-maksu tilaajan-maksu}
                                  (when (::valikatselmus/paatoksen-id lomake)
                                    {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id lomake)}))]
      [:div.paatos
       [:div.paatos-numero 1]
       [:div.paatos-sisalto
        [:h3 (str "Tavoitehinnan ylitys " (fmt/desimaaliluku ylityksen-maara))]
        (if muokattava?
          [:<>
           [:p "Urakoitsija maksaa hyvitystä ylityksestä"]
           [paatos-maksu-lomake e! app :tavoitehinnan-ylitys-lomake ylityksen-maara]]
          [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksu-prosentteina) "%"]])
        (when (and urakoitsijan-maksu maksu-prosentteina)
          [:div.osuusrivit
           [:p.osuusrivi "Urakoitsijan osuus " [:strong (fmt/desimaaliluku urakoitsijan-maksu)] " (" (fmt/desimaaliluku maksu-prosentteina) "%)"]
           [:p.osuusrivi "Tilaajan osuus " [:strong (fmt/desimaaliluku tilaajan-maksu)] " (" (fmt/desimaaliluku (- 100 maksu-prosentteina)) "%)"]])
        (if muokattava?
          [napit/yleinen-ensisijainen "Tallenna päätös"
           #(e! (t/->TallennaPaatos paatoksen-tiedot))
           {:disabled (seq (-> app :tavoitehinnan-ylitys-lomake ::lomake/virheet))}]
          [napit/muokkaa "Muokkaa päätöstä" #(e! (t/->MuokkaaPaatosta :tavoitehinnan-ylitys-lomake)) {:luokka "napiton-nappi"}])]])))

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
    (when tavoitehinnan-ylitys?
      [tavoitehinnan-ylitys-lomake e! app toteuma oikaistu-tavoitehinta])))

(defn valikatselmus [e! app]
  (komp/luo
    (komp/sisaan #(do (println "valikatselmus sisaan")
                      (when (nil? (:urakan-paatokset app)) (e! (t/->HaeUrakanPaatokset (-> @tila/yleiset :urakka :id))))))
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
