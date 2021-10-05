(ns harja.views.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.roolit :as roolit]
            [harja.domain.urakka :as urakka]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.urakka.kulut.valikatselmus :as valikatselmus-tiedot]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [harja.tiedot.urakka.lupaus-tiedot :as lupaus-tiedot]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.views.urakka.kulut.yhteiset :as yhteiset]))

(defn onko-oikeudet-tehda-paatos? [urakka-id]
  (or
    (roolit/roolissa? @istunto/kayttaja roolit/ely-urakanvalvoja)
    (roolit/jvh? @istunto/kayttaja)
    (roolit/rooli-urakassa? @istunto/kayttaja roolit/ely-urakanvalvoja urakka-id)))

(defn- onko-hoitokausi-tulevaisuudessa? [hoitokausi nykyhetki]
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first hoitokausi))
        nykykuukausi (pvm/kuukausi nykyhetki)
        nykyvuosi (pvm/vuosi nykyhetki)]
    (cond
      ;; Alkaa samana vuonna, mutta ei olla vielä syksyssä tarpeeksi pitkällä
      (and
          (= hoitokauden-alkuvuosi nykyvuosi)
          (< nykykuukausi 10))
      true
      ;; On alkanut aiempana vuonna
      (< hoitokauden-alkuvuosi nykyvuosi)
      false
      ;; Alkaa myöhemmin vuoden perusteella
      (> hoitokauden-alkuvuosi nykyvuosi)
      true
      ;; Jää case, jossa vuosi on sama ja kuukausi on suurempi
      :else true)))

(defn- onko-hoitokausi-menneisyydessa?
  "Tulkitaan, että hoitokausi on menneisyydessä, jos se on päättynyt edellisenä vuonna. Eli jos nykyhetki on 31.12.2021
  ja hoitopäättyy 30.09.2021 niin hoitokausi ei ole vielä menneisyydessä. Tehdään tulkinta tässä vaiheessa niin, että
  käyttäjille jää n. 3kk aikaa tehdä välikatselmukset ja sen jälkeen se lukitaan."
  [hoitokausi nykyhetki urakan-alkuvuosi]
  (let [hoitokauden-loppuvuosi (pvm/vuosi (second hoitokausi))
        nykyvuosi (pvm/vuosi nykyhetki)
        vanha-mhu? (or (= 2019 urakan-alkuvuosi) (= 2020 urakan-alkuvuosi) false)]
    (cond
      ;; Vanhemman MH urakat saa täyttää päätöksiä, vaikka hoitokausi olisi menneisyydessä
      (and vanha-mhu? (> nykyvuosi hoitokauden-loppuvuosi))
      false

      (and (not vanha-mhu?) (> nykyvuosi hoitokauden-loppuvuosi))
      true
      :else
      false)))

(defn valikatselmus-otsikko-ja-tiedot [app]
  (let [urakan-nimi (:nimi @nav/valittu-urakka)
        valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        urakan-alkuvuosi (pvm/vuosi (:alkupvm @nav/valittu-urakka))
        ;; Joskus valittua hoitokautta ei ole asetettu
        valittu-hoitokausi (if (:valittu-hoitokausi app)
                            (:valittu-hoitokausi app)
                            [(pvm/hoitokauden-alkupvm valittu-hoitokauden-alkuvuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc valittu-hoitokauden-alkuvuosi)))])
        hoitokausi-str (pvm/paivamaaran-hoitokausi-str (pvm/hoitokauden-alkupvm valittu-hoitokauden-alkuvuosi))
        nykyhetki (pvm/nyt)
        hoitokausi-tulevaisuudessa? (onko-hoitokausi-tulevaisuudessa? valittu-hoitokausi nykyhetki)
        hoitokausi-menneisyydessa? (onko-hoitokausi-menneisyydessa? valittu-hoitokausi nykyhetki urakan-alkuvuosi)
        jvh? (roolit/jvh? @istunto/kayttaja)]
    [:<>
     [:h1 "Välikatselmuksen päätökset"]
     [:div.caption urakan-nimi]
     [:div.caption (str (inc (- valittu-hoitokauden-alkuvuosi urakan-alkuvuosi)) ". hoitovuosi (" hoitokausi-str ")")]

     ;; Varoitetaan kaikkia muita paitsi järjestelmävalvojaa, ettei välikatselmusta voida tehdä
     (when (and hoitokausi-tulevaisuudessa? (not jvh?))
       [:div.valikatselmus-tulevaisuudessa-varoitus {:style {:margin-top "16px"}}
        [ikonit/harja-icon-status-alert]
        [:span "Hoitovuodelle ei voi tässä vaiheessa tehdä välikatselmusta."]])
     (when (and hoitokausi-menneisyydessa? (not jvh?))
       [:div.valikatselmus-menneisyydessa-varoitus {:style {:margin-top "16px"}}
           [ikonit/harja-icon-status-alert]
           [:span "Hoitovuosi on päättynyt ja välikatselmusta ei voi enää muokata."]])]))

(defn tavoitehinnan-oikaisut [_ app]
  (let [tallennettu-tila (atom (get-in app [:tavoitehinnan-oikaisut (:hoitokauden-alkuvuosi app)]))
        virheet (atom {})]
    (fn [e! {:keys [tavoitehinnan-oikaisut hoitokauden-alkuvuosi] :as app}]
      (let [oikaisut-atom (reagent.core/cursor tila/tavoitehinnan-oikaisut [(:hoitokauden-alkuvuosi app)])
            paatoksia? (not (empty? (:urakan-paatokset app)))
            hoitokauden-oikaisut (get tavoitehinnan-oikaisut hoitokauden-alkuvuosi)
            nykyhetki (pvm/nyt)
            ;; Joskus valittua hoitokautta ei ole asetettu
            valittu-hoitokausi (if (:valittu-hoitokausi app)
                                 (:valittu-hoitokausi app)
                                 [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                                  (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi)))])
            urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
            ;; Muokkaaminen on järjestelmävalvojalle aina sallittua, mutta muut on rajoitettu myös ajan perusteella
            voi-muokata? (or (roolit/jvh? @istunto/kayttaja)
                             (and (onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
                                  (not (onko-hoitokausi-tulevaisuudessa? valittu-hoitokausi nykyhetki))
                                  (not (onko-hoitokausi-menneisyydessa? valittu-hoitokausi nykyhetki urakan-alkuvuosi))))]
        [:div
         [grid/muokkaus-grid
          {:otsikko "Tavoitehinnan oikaisut"
           :tyhja "Ei oikaisuja"
           :voi-kumota? false
           :voi-muokata? voi-muokata?
           :toimintonappi-fn (when voi-muokata?
                               (fn [rivi _muokkaa! id]
                                 [napit/poista ""
                                  #(do
                                     (e! (valikatselmus-tiedot/->PoistaOikaisu rivi id))
                                     (reset! tallennettu-tila hoitokauden-oikaisut))
                                  {:luokka "napiton-nappi"}]))
           :uusi-rivi-nappi-luokka "nappi-reunaton"
           :lisaa-rivi "Lisää oikaisu"
           :validoi-uusi-rivi? false
           :on-rivi-blur (fn [oikaisu i]
                           (when-not (or (= @tallennettu-tila hoitokauden-oikaisut)
                                         (seq (get @virheet i))
                                         (:koskematon (get hoitokauden-oikaisut i)))
                             (e! (valikatselmus-tiedot/->TallennaOikaisu oikaisu i))
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
            :valinta-arvo identity
            :valinta-nayta {:lisays "Lisäys" ;; TODO: Korjaa lukumoodissa
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

         (when (and paatoksia? voi-muokata?)
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
    (> palkkio-euroina (* valikatselmus/+maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta))))

;; vertailtava-summa on ylityksen tai tavoitepalkkion määrä.
(defn paatos-maksu-lomake
  ([e! app paatos-avain vertailtava-summa voi-muokata?]
   (paatos-maksu-lomake e! app paatos-avain vertailtava-summa voi-muokata? nil))
  ([e! app paatos-avain vertailtava-summa voi-muokata? oikaistu-tavoitehinta]
   (let [lomake (paatos-avain app)
         alitus? (= :tavoitehinnan-alitus-lomake paatos-avain)
         maksimi-tavoitepalkkio (min vertailtava-summa (* valikatselmus/+maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta))
         maksimi-tavoitepalkki-prosenttina (* 100 (/ maksimi-tavoitepalkkio vertailtava-summa))]
     [:div.maksu-kentat
      [lomake/lomake {:ei-borderia? true
                      :muokkaa! #(e! (valikatselmus-tiedot/->PaivitaPaatosLomake % paatos-avain))
                      :kutsu-muokkaa-renderissa? true
                      :tarkkaile-ulkopuolisia-muutoksia? true
                      :validoi-alussa? true}
       [{:nimi :maksu
         :piilota-label? true
         ::lomake/col-luokka "col-md-4 margin-top-16 paatos-maksu"
         :tyyppi :positiivinen-numero
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

(defn urakalla-ei-tavoitehintaa-varoitus []
  [:div.tavoitehinta-tyhja-varoitus.margin-top-16
   [ikonit/livicon-warning-sign]
   [:span
    [:p
     [:strong
      "Hoitokaudelle ei ole asetettu tavoitehintaa!"]]
    [:p "Täytä tavoitehinta suunnitteluosiossa valitulle hoitokaudelle"]]])

(defn tavoitehinnan-ylitys-lomake [e! app toteuma oikaistu-tavoitehinta oikaistu-kattohinta tavoitehinta voi-muokata?]
  (let [ ;; Maksimi ylitys on 10% tavoitehinnasta eli kattohinnan alle jäävä määrä
        ylityksen-maara (if (> toteuma oikaistu-kattohinta)
                          (- oikaistu-kattohinta oikaistu-tavoitehinta)
                          (- toteuma oikaistu-tavoitehinta))
        lomake (:tavoitehinnan-ylitys-lomake app)
        hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        muokkaustila? (or (not (::valikatselmus/paatoksen-id lomake)) (:muokataan? lomake))
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
      {:class ["paatos-check" (when muokkaustila? "ei-tehty")]}
      [ikonit/livicon-check]]
     [:div.paatos-sisalto
      [:h3 (str "Tavoitehinnan ylitys " (fmt/desimaaliluku ylityksen-maara))]
      (if muokkaustila?
        (if voi-muokata?
          [:<>
           [:p "Urakoitsija maksaa hyvitystä ylityksestä"]
           [paatos-maksu-lomake e! app :tavoitehinnan-ylitys-lomake ylityksen-maara voi-muokata?]]

          [:p "Aluevastaava tekee päätöksen tavoitehinnan ylityksestä"]) ;; FIXME: Ei figma-speksiä, korjaa kunhan sellainen löytyy.
        [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku urakoitsijan-maksu) "€"]])
      (when (and voi-muokata? urakoitsijan-maksu maksu-prosentteina)
        [:div.osuusrivit
         [:p.osuusrivi "Urakoitsijan osuus " [:strong (fmt/desimaaliluku urakoitsijan-maksu)] "€ (" (fmt/desimaaliluku maksu-prosentteina) "%)"]
         [:p.osuusrivi "Tilaajan osuus " [:strong (fmt/desimaaliluku tilaajan-maksu)] "€ (" (fmt/desimaaliluku (- 100 maksu-prosentteina)) "%)"]])
      (when voi-muokata?
        (if muokkaustila?
          [:<>
           (when (and voi-muokata? (nil? tavoitehinta))
             [urakalla-ei-tavoitehintaa-varoitus])
           [napit/yleinen-ensisijainen "Tallenna päätös"
            #(e! (valikatselmus-tiedot/->TallennaPaatos paatoksen-tiedot))
            {:disabled (seq (-> app :tavoitehinnan-ylitys-lomake ::lomake/virheet))}]]
          [napit/muokkaa "Muokkaa päätöstä" #(e! (valikatselmus-tiedot/->MuokkaaPaatosta :tavoitehinnan-ylitys-lomake)) {:luokka "napiton-nappi"}]))]]))

(defn tavoitehinnan-alitus-lomake [e! {:keys [hoitokauden-alkuvuosi tavoitehinnan-alitus-lomake] :as app} toteuma oikaistu-tavoitehinta tavoitehinta voi-muokata?]
  (let [alituksen-maara (- oikaistu-tavoitehinta toteuma)
        tavoitepalkkio (* valikatselmus/+tavoitepalkkio-kerroin+ alituksen-maara)
        ;; Maksimi maksettava tavoitepalkkio, eli jos yli 30% tavoitehinnan alituksesta, yli jäävä osa on pakko siirtää.
        maksimi-tavoitepalkkio (* valikatselmus/+maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta)
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
        ei-tallennettava? (or (nil? tavoitehinta) (and osa-valittu? (seq (::lomake/virheet tavoitehinnan-alitus-lomake))))
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
       (if voi-muokata?
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
                                                               [paatos-maksu-lomake e! app :tavoitehinnan-alitus-lomake tavoitepalkkio voi-muokata? oikaistu-tavoitehinta]
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
                        #(e! (valikatselmus-tiedot/->PaivitaTavoitepalkkionTyyppi %)))]])]
           [:p.maksurivi "Urakoitsijalle maksetaan tavoitepalkkiota " [:strong (fmt/desimaaliluku maksettava-palkkio-euroina) "€"]])

         (if (::valikatselmus/paatoksen-id tavoitehinnan-alitus-lomake)
           [:<>
            (when (pos? maksettava-palkkio-euroina)
              [:p.maksurivi "Urakoitsijalle maksetaan tavoitepalkkiota " [:strong (fmt/desimaaliluku maksettava-palkkio-euroina) "€"]])
            (when (pos? siirto)
              [:p.maksurivi "Siirretään ensi vuoden alennukseksi " [:strong (fmt/desimaaliluku siirto) "€"]])]
           [:p "Aluevastaava tekee päätöksen tavoitehinnan alituksesta"]))

       (when voi-muokata?
         (if muokattava?
           [:<>
            (when (nil? tavoitehinta)
              [urakalla-ei-tavoitehintaa-varoitus])
            [napit/yleinen-ensisijainen "Tallenna päätös"
             #(e! (valikatselmus-tiedot/->TallennaPaatos paatoksen-tiedot))
             {:disabled ei-tallennettava?}]]
           [napit/muokkaa "Muokkaa päätöstä" #(e! (valikatselmus-tiedot/->MuokkaaPaatosta :tavoitehinnan-alitus-lomake)) {:luokka "napiton-nappi"}]))]]]))

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
                                                       #(e! (valikatselmus-tiedot/->PaivitaPaatosLomake (assoc kattohinnan-ylitys-lomake :siirto %) :kattohinnan-ylitys-lomake)))}]])

(defn kattohinnan-ylitys-lomake [e! {:keys [hoitokauden-alkuvuosi kattohinnan-ylitys-lomake] :as app} toteuma oikaistu-kattohinta tavoitehinta voi-muokata?]
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
       (if voi-muokata?
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
                        #(e! (valikatselmus-tiedot/->PaivitaMaksunTyyppi %)))])
             (if siirto-valittu?
               [:p.maksurivi "Siirretään ensi vuoden kustannuksiksi " [:strong (fmt/desimaaliluku siirto) " €"]]
               [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksettava-summa) " €"] " (" (fmt/desimaaliluku maksettava-summa-prosenttina) " %)"])]]
           [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksettava-summa) "€"]])

         ;; FIXME: Ei figma-speksiä, korjaa kunhan sellainen löytyy.
         (if (::valikatselmus/paatoksen-id kattohinnan-ylitys-lomake)
           [:<>
            (when (pos? maksettava-summa)
              [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksettava-summa) "€"]])
            (when (pos? siirto)
              [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/desimaaliluku maksettava-summa) " €"] " (" (fmt/desimaaliluku maksettava-summa-prosenttina) " %)"])]
           [:p "Aluevastaava tekee päätöksen kattohinnan ylityksestä"]))

       (when voi-muokata?
         (if muokattava?
           [:<>
            (when (nil? tavoitehinta)
              [urakalla-ei-tavoitehintaa-varoitus])
            [napit/yleinen-ensisijainen "Tallenna päätös"
             #(e! (valikatselmus-tiedot/->TallennaPaatos paatoksen-tiedot))
             {:disabled (and osa-valittu? (seq (::lomake/virheet kattohinnan-ylitys-lomake)))}]]
           [napit/muokkaa "Muokkaa päätöstä" #(e! (valikatselmus-tiedot/->MuokkaaPaatosta :kattohinnan-ylitys-lomake)) {:luokka "napiton-nappi"}]))]]]))

(defn lupaus-lomake [e! oikaistu-tavoitehinta app]
  (let [yhteenveto (:yhteenveto app)
        hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        paatos-tehty? (or (= :katselmoitu-toteuma (:ennusteen-tila yhteenveto)) false)
        luvatut-pisteet (get-in app [:lupaus-sitoutuminen	:pisteet])
        toteutuneet-pisteet (get-in app [:yhteenveto :pisteet :toteuma])
        tavoitehinta (get-in app [:yhteenveto :tavoitehinta])
        lupaus-bonus (get-in app [:yhteenveto :bonus-tai-sanktio :bonus])
        lupaus-sanktio (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio])
        tavoite-taytetty? (get-in app [:yhteenveto :bonus-tai-sanktio :tavoite-taytetty])
        urakoitsijan-maksu (cond lupaus-sanktio lupaus-sanktio
                                 tavoite-taytetty? 0M
                                 :else nil)
        tilaajan-maksu (cond lupaus-bonus lupaus-bonus
                             tavoite-taytetty? 0M
                             :else nil)
        summa (cond
                lupaus-sanktio lupaus-sanktio
                lupaus-bonus lupaus-bonus
                tavoite-taytetty? 0M)
        pisteet (get-in app [:yhteenveto :pisteet :toteuma])
        sitoutumis-pisteet (get-in app [:lupaus-sitoutuminen :pisteet])
        lupaus-tyyppi (if (or lupaus-bonus tavoite-taytetty?) ::valikatselmus/lupaus-bonus ::valikatselmus/lupaus-sanktio)
        lomake-avain (if (or lupaus-bonus tavoite-taytetty?) :lupaus-bonus-lomake :lupaus-sanktio-lomake)
        paatos-id (get-in app [lomake-avain ::valikatselmus/paatoksen-id])
        paatoksen-tiedot (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                                 ::valikatselmus/tyyppi lupaus-tyyppi
                                 ::valikatselmus/urakoitsijan-maksu urakoitsijan-maksu
                                 ::valikatselmus/tilaajan-maksu tilaajan-maksu
                                 ::valikatselmus/lupaus-luvatut-pisteet luvatut-pisteet
                                 ::valikatselmus/lupaus-toteutuneet-pisteet toteutuneet-pisteet
                                 ::valikatselmus/lupaus-tavoitehinta oikaistu-tavoitehinta
                                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                 ::valikatselmus/siirto nil}
                                (when (get-in app [lomake-avain ::valikatselmus/paatoksen-id])
                                  {::valikatselmus/paatoksen-id paatos-id}))
        on-oikeudet? (onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        muokattava? (or (get-in app [lomake-avain :muokataan?]) false)]
    [:div
     [:div.paatos
      [:div
       {:class ["paatos-check" (when-not paatos-tehty? "ei-tehty")]}
       [ikonit/livicon-check]]

      [:div.paatos-sisalto {:style {:flex-grow 7}}
       (cond
         lupaus-sanktio
         [:h3 "Lupaukset: Urakoitsija maksaa sakkoa " (fmt/desimaaliluku summa) " € luvatun pistemäärän alittamisesta."]
         lupaus-bonus
         [:h3 (str "Lupaukset: Urakoitsija saa bonusta " (fmt/desimaaliluku summa) " € luvatun pistemäärän ylittämisestä.")]
         tavoite-taytetty?
         [:h3 (str "Lupaukset: Urakoitsija pääsi tavoitteeseen.")])
       [:p "Urakoitsija sai " pisteet " ja lupasi " sitoutumis-pisteet " pistettä." " Tavoitehinta: " (fmt/desimaaliluku tavoitehinta) " €."]
       [:div {:style {:padding-top "22px"}}
        (cond
          (or lupaus-bonus lupaus-sanktio)
          [:<>
           [ikonit/harja-icon-status-completed]
           (if lupaus-sanktio
             " Urakoitsija maksaa sanktiota "
             " Maksetaan urakoitsijalle bonusta ")
           [:strong (fmt/desimaaliluku summa) " € "]
           "(100%)"]

          tavoite-taytetty?
          [:<>
           [ikonit/harja-icon-status-completed]
           " Urakoitsija ei saa bonusta eikä sanktiota."]

          :else nil)]
       [:div.flex-row

        ;; Muokkaa, eli poista päätös, tai jos sitä ei ole tehty, niin tee päätös
        (if
          (or
            muokattava?
            (and (not paatos-tehty?) (= :alustava-toteuma (:ennusteen-tila yhteenveto))))
          [:div {:style {:flex-grow 1}}
           (if on-oikeudet?
             [napit/yleinen-ensisijainen "Tallenna päätös"
              #(e! (valikatselmus-tiedot/->TallennaPaatos
                     ;; Lupaus-päätös tallennetaan aina uutena tai poistetaan - ei muokata
                     (dissoc paatoksen-tiedot ::valikatselmus/paatoksen-id)))]
             (if lupaus-sanktio
               [:p "Aluevastaava tekee päätöksen sanktion maksamisesta."]
               [:p "Aluevastaava tekee päätöksen bonuksen maksamisesta."]))]
          [:div {:style {:flex-grow 1}}
           (if on-oikeudet?
             [napit/nappi
              "Kumoa päätös"
              #(e! (valikatselmus-tiedot/->PoistaLupausPaatos paatos-id))
              {:luokka "nappi-toissijainen napiton-nappi"
               :ikoni [ikonit/harja-icon-action-undo]}]
             (if lupaus-sanktio
               [:p "Aluevastaava tekee päätöksen sanktion maksamisesta."]
               [:p "Aluevastaava tekee päätöksen bonuksen maksamisesta."]))])
        [:div {:style {:flex-grow 1
                       :padding "2rem 0 0 2rem"
                       :text-align "right"}}
         [harja.ui.yleiset/linkki "Siirry lupauksiin"
          #(siirtymat/avaa-lupaukset (:hoitokauden-alkuvuosi app))]]]]]]))

(defn lupaus-ilmoitus
  "Kun lupaukset eivät ole valmiita, näytetään ilmoitus, että ne pitäisi tehdä valmiiksi."
  [e! app]
  [:<>
   [:div.paatos
    [:div
     {:class (str "paatos-check ei-tehty")}]
    [:div.paatos-sisalto
     [:h3 "Lupaukset: hoitovuoden lupaukset eivät ole vielä valmiita."]
     [:p "Kaikista lupauksista pitää olla viimeinen päättävä merkintä tehty ennen kuin maksupäätöksen voi tehdä."]

     [:p {:style {:padding "2rem 0 0 2rem"}}
      [harja.ui.yleiset/linkki "Siirry lupauksiin"
       #(siirtymat/avaa-lupaukset (:hoitokauden-alkuvuosi app))]]]]])

(defn paatokset [e! app]
  (let [hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        hoitokausi-nro (urakka-tiedot/hoitokauden-jarjestysnumero hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        oikaisujen-summa (t-yhteiset/oikaisujen-summa (:tavoitehinnan-oikaisut app) hoitokauden-alkuvuosi)
        tavoitehinta (kustannusten-seuranta-tiedot/hoitokauden-tavoitehinta hoitokausi-nro app)
        kattohinta (or (kustannusten-seuranta-tiedot/hoitokauden-kattohinta hoitokausi-nro app) 0)
        oikaistu-tavoitehinta (+ oikaisujen-summa (or tavoitehinta 0))
        oikaistu-kattohinta (+ oikaisujen-summa kattohinta)
        toteuma (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        alitus? (> oikaistu-tavoitehinta toteuma)
        tavoitehinnan-ylitys? (< oikaistu-tavoitehinta toteuma)
        kattohinnan-ylitys? (< oikaistu-kattohinta toteuma)
        lupaukset-valmiina? (#{:katselmoitu-toteuma :alustava-toteuma} (get-in app [:yhteenveto :ennusteen-tila]))
        nykyhetki (pvm/nyt)
        ;; Joskus valittua hoitokautta ei ole asetettu
        valittu-hoitokausi (if (:valittu-hoitokausi app)
                             (:valittu-hoitokausi app)
                             [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                              (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi)))])
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        ;; Muokkaaminen on järjestelmävalvojalle aina sallittua, mutta muut on rajoitettu myös ajan perusteella
        voi-muokata? (or (roolit/jvh? @istunto/kayttaja)
                         (and
                           (onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
                           (not (onko-hoitokausi-tulevaisuudessa? valittu-hoitokausi nykyhetki))
                           (not (onko-hoitokausi-menneisyydessa? valittu-hoitokausi nykyhetki urakan-alkuvuosi))))]

    ;; Piilotetaan kaikki mahdollisuudet tehdä päätös, jos tavoitehintaa ei ole asetettu.
    (when (and oikaistu-tavoitehinta (> oikaistu-tavoitehinta 0))
      [:div
       [:h2 "Budjettiin liittyvät päätökset"]
       (when tavoitehinnan-ylitys?
         [tavoitehinnan-ylitys-lomake e! app toteuma oikaistu-tavoitehinta oikaistu-kattohinta tavoitehinta voi-muokata?])
       (when kattohinnan-ylitys?
         [kattohinnan-ylitys-lomake e! app toteuma oikaistu-kattohinta tavoitehinta voi-muokata?])
       (when alitus?
         [tavoitehinnan-alitus-lomake e! app toteuma oikaistu-tavoitehinta tavoitehinta voi-muokata?])
       [:h2 "Lupauksiin liittyvät päätökset"]
       (if lupaukset-valmiina?
         [lupaus-lomake e! oikaistu-tavoitehinta app]
         [lupaus-ilmoitus e! app])])))

(defn valikatselmus [e! app]
  (komp/luo
    (komp/sisaan #(do
                    (e! (lupaus-tiedot/->HaeUrakanLupaustiedot (:urakka @tila/yleiset)))
                    (if (nil? (:urakan-paatokset app))
                      (e! (valikatselmus-tiedot/->HaeUrakanPaatokset (-> @tila/yleiset :urakka :id)))
                      (e! (valikatselmus-tiedot/->AlustaPaatosLomakkeet (:urakan-paatokset app) (:hoitokauden-alkuvuosi app))))))
    (fn [e! app]
      [:div.valikatselmus-container
       [debug/debug app]
       [napit/takaisin "Sulje välikatselmus" #(e! (kustannusten-seuranta-tiedot/->SuljeValikatselmusLomake)) {:luokka "napiton-nappi tumma"}]
       [valikatselmus-otsikko-ja-tiedot app]
       [:div.valikatselmus-ja-yhteenveto
        [:div.oikaisut-ja-paatokset
         [tavoitehinnan-oikaisut e! app]
         [paatokset e! app]
         [:div {:style {:padding-top "16px"}}
          [napit/yleinen-toissijainen "Sulje välikatselmus" #(e! (kustannusten-seuranta-tiedot/->SuljeValikatselmusLomake))]]]
        [:div.yhteenveto-container
         [yhteiset/yhteenveto-laatikko e! app (:kustannukset app) :valikatselmus]]]])))
