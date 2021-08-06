(ns harja.views.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.tiedot.urakka.kulut.valikatselmus :as t]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.kulut.yhteiset :as yhteiset]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.ui.validointi :as validointi]))

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

(defn tavoitehinnan-oikaisut [e! app]
  (let [virheet (atom nil)
        tallennettu-tila (atom @(:tavoitehinnan-oikaisut-atom app))]
    (fn [e! {:keys [tavoitehinnan-oikaisut-atom] :as app}]
      [grid/muokkaus-grid
       {:otsikko "Tavoitehinnan oikaisut"
        :voi-kumota? false
        :voi-lisata? false
        :custom-toiminto {:teksti "Lisää Oikaisu"
                          :toiminto #(e! (t/->LisaaOikaisu))
                          :opts {:ikoni (ikonit/livicon-plus)
                                 :luokka "nappi-ensisijainen"}}
        :toimintonappi-fn (fn [rivi muokkaa!]
                            [napit/poista ""
                             #(do
                                (e! (t/->PoistaOikaisu rivi muokkaa!))
                                (reset! tallennettu-tila @tavoitehinnan-oikaisut-atom))
                             {:luokka "napiton-nappi"}])
        :uusi-rivi-nappi-luokka "nappi-ensisijainen"
        :on-rivi-blur (fn [oikaisu i]
                        (do
                          (when-not (and (= @tallennettu-tila @tavoitehinnan-oikaisut-atom))
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
                              (e! (t/->TallennaOikaisu oikaisu i))
                              (reset! tallennettu-tila @tavoitehinnan-oikaisut-atom)))))
        :virheet virheet}
       [{:otsikko "Luokka"
         :nimi ::valikatselmus/otsikko
         :tyyppi :valinta
         :valinnat valikatselmus/luokat
         :leveys 2}
        {:otsikko "Selite"
         :nimi ::valikatselmus/selite
         :tyyppi :string
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
         :leveys 2}]
       tavoitehinnan-oikaisut-atom])))

(defn- kaanna-euro-ja-prosentti [vanhat-tiedot uusi-valinta ylitys-tai-alitus]
  (let [vanha-maksu (:maksu vanhat-tiedot)]
    (println "kaanna " vanhat-tiedot uusi-valinta)
    (assoc (assoc vanhat-tiedot :maksu (if (= :prosentti uusi-valinta)
                                         (* 100 (/ vanha-maksu ylitys-tai-alitus))
                                         (/ (* vanha-maksu ylitys-tai-alitus) 100)))
      :euro-vai-prosentti uusi-valinta)))

(defn paatos-maksu-lomake [e! app paatos-avain ylitys-tai-alitus-maara]
  (let [lomake (paatos-avain app)
        validaatioteksti (if (:tavoitehinnan-ylitys-lomake paatos-avain)
                           {:ei-tyhja "Anna hyvityksen määrä"
                            :yli-100-prosenttia "Hyvityksen määrä ei voi olla yli 100% tavoitehinnasta"}
                           {})]
    [:div.maksu-kentat
     [lomake/lomake {:ei-borderia? true
                     :muokkaa! #(e! (t/->PaivitaPaatosLomake % paatos-avain ylitys-tai-alitus-maara))
                     :tarkkaile-ulkopuolisia-muutoksia? true}
      [{:nimi :maksu
        :piilota-label? true
        ::lomake/col-luokka "col-md-4 margin-top-16 paatos-maksu"
        :tyyppi :numero
        :vayla-tyyli? true
        :virhe? (validointi/nayta-virhe? [:maksu] lomake)
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
      (paatos-avain app)]]))

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
    [:<>
     [debug/debug {:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                   :oikaisujen-summa oikaisujen-summa
                   :tavoitehinta tavoitehinta
                   :kattohinta kattohinta
                   :oikaistu-tavoitehinta oikaistu-tavoitehinta
                   :oikaistu-kattohinta oikaistu-kattohinta
                   :toteuma toteuma
                   :alitus? alitus?
                   :tavoitehinnan-ylitys? tavoitehinnan-ylitys?
                   :kattohinnan-ylitys? kattohinnan-ylitys?}]
     (when tavoitehinnan-ylitys?
       (let [ylityksen-maara (- toteuma oikaistu-tavoitehinta)
             maksu-prosentteina? (= :prosentti (-> app :tavoitehinnan-ylitys-lomake :euro-vai-prosentti))
             maksu (-> app :tavoitehinnan-ylitys-lomake :maksu)
             maksu-rahana (if maksu-prosentteina?
                            (/ (* maksu ylityksen-maara) 100)
                            maksu)
             maksu-prosentteina (if maksu-prosentteina?
                                  maksu
                                  (* 100 (/ maksu ylityksen-maara)))]
         [:div.paatos
          [:div.paatos-numero 1]
          [:div.paatos-sisalto
           [:h3 (str "Tavoitehinnan ylitys " (fmt/desimaaliluku ylityksen-maara))]
           [:p "Urakoitsija maksaa hyvitystä"]
           [paatos-maksu-lomake e! app :tavoitehinnan-ylitys-lomake ylityksen-maara]
           (when (and maksu-rahana maksu-prosentteina)
             [:div.osuusrivit
              [:p.osuusrivi "Urakoitsijan osuus " [:strong (fmt/desimaaliluku maksu-rahana)] " (" (fmt/desimaaliluku maksu-prosentteina) "%)"]
              [:p.osuusrivi "Tilaajan osuus " [:strong (fmt/desimaaliluku (- ylityksen-maara maksu-rahana))] " (" (fmt/desimaaliluku (- 100 maksu-prosentteina)) "%)"]])
           [napit/yleinen-ensisijainen "Tallenna päätös" #() {:disabled (not (-> app :tavoitehinnan-ylitys-lomake ::tila/validi?))}]]]))
     [debug/debug app]
     ]))

(defn valikatselmus [e! app]
  [:div.valikatselmus-container
   [napit/takaisin "Takaisin" #(e! (kustannusten-seuranta-tiedot/->SuljeValikatselmusLomake)) {:luokka "napiton-nappi tumma"}]
   [valikatselmus-otsikko-ja-tiedot app]
   [:div.valikatselmus-ja-yhteenveto
    [:div.oikaisut-ja-paatokset
     [tavoitehinnan-oikaisut e! app]
     [paatokset e! app :tavoitehinnan-ylitys-lomake]]
    [:div.yhteenveto-container
     [yhteiset/yhteenveto-laatikko e! app (:kustannukset app) :valikatselmus]]]])
