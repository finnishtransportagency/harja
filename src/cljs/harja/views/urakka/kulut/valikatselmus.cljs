(ns harja.views.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.roolit :as roolit]
            [harja.domain.urakka :as urakka]
            [harja.domain.lupaus-domain :as lupaus-domain]
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
            [harja.tyokalut.yleiset :as tyokalut]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.views.urakka.kulut.yhteiset :as yhteiset]))

;; TODO: Parempi olisi muokata tämä käyttämään normaalia oikeustarkistusta.
(defn onko-oikeudet-tehda-paatos? [urakka-id]
  (or
    (roolit/roolissa? @istunto/kayttaja roolit/ely-urakanvalvoja)
    (roolit/roolissa? @istunto/kayttaja roolit/ely-paakayttaja)
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
  ;; Niin moni urakka ei ole tehnyt välikatselmusta, että otetaan tarkistus hetkeksi pois käytöstä
  false
  #_ (let [hoitokauden-loppuvuosi (pvm/vuosi (second hoitokausi))
        nykyvuosi (pvm/vuosi nykyhetki)
        vanha-mhu? (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)]
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
        [:span "Hoitovuosi on lukittu vuoden vaihteessa ja välikatselmusta ei voi enää muokata."]])]))

(defn kattohinnan-oikaisu [e! app]
  (let [oikaistu-kattohinta (some->
                              (yhteiset/kattohinnan-oikaisu-valitulle-vuodelle app)
                              ::valikatselmus/uusi-kattohinta)
        uusi-kattohinta (get-in app [:kattohinnan-oikaisu :uusi-kattohinta])
        tavoitehinta (t-yhteiset/oikaistu-tavoitehinta-valitulle-hoitokaudelle app)
        uusi-kattohinta-suurempi-kuin-tavoitehinta? (and uusi-kattohinta tavoitehinta (>= uusi-kattohinta tavoitehinta))
        uusi-kattohinta-validi? uusi-kattohinta-suurempi-kuin-tavoitehinta?
        muokkaa-painettu? (get-in app [:kattohinnan-oikaisu :muokkaa-painettu?])
        muokkaustila? (or muokkaa-painettu? (not oikaistu-kattohinta))]
    [:<>
     [:div.oikaisu-paatos-varoitus
      [ikonit/harja-icon-status-alert]
      [:span "Jos tavoitehinnan oikaisun myötä myös kattohinta muuttuu, syötä uusi oikaistu kattohinta."]]
     [:div.caption.semibold {:style {:font-size "12px"}} "Oikaistu kattohinta"]
     [:div.flex-row.alkuun.valistys16
      (if muokkaustila?
        [kentat/tee-kentta
         {:tyyppi :positiivinen-numero
          :koko 20
          :vayla-tyyli? true
          :max-desimaalit 7
          :kokonaisosan-maara 9
          :fmt fmt/euro-opt}
         (r/wrap (or
                   ;; Muokattu arvo
                   uusi-kattohinta
                   ;; Tietokantaan tallennettu arvo
                   oikaistu-kattohinta)
           (fn [kattohinta]
             (e! (valikatselmus-tiedot/->KattohinnanOikaisuaMuokattu kattohinta))))]
        [:span {:style {:min-width "173px"}}
         (fmt/euro-opt oikaistu-kattohinta)])

      (if muokkaustila?
        [napit/tallenna
         "Hyväksy uusi kattohinta"
         #(e! (valikatselmus-tiedot/->TallennaKattohinnanOikaisu))
         {:disabled (not uusi-kattohinta-validi?)}]
        [napit/muokkaa
         "Muokkaa"
         #(e! (valikatselmus-tiedot/->KattohinnanMuokkaaPainettu oikaistu-kattohinta))])
      (when (and muokkaustila? oikaistu-kattohinta)
        [napit/poista
         "Poista kattohinnan oikaisu"
         #(e! (valikatselmus-tiedot/->PoistaKattohinnanOikaisu))])]]))

(defn tavoitehinnan-oikaisut [e! {:keys [urakan-paatokset valittu-hoitokausi tavoitehinnan-oikaisut hoitokauden-alkuvuosi] :as app}]
  (let [paatoksia? (seq urakan-paatokset)
        hoitokauden-oikaisut (get tavoitehinnan-oikaisut hoitokauden-alkuvuosi)
        hoitokauden-oikaisut-atom (atom hoitokauden-oikaisut)
        nykyhetki (pvm/nyt)
        ;; Joskus valittua hoitokautta ei ole asetettu
        valittu-hoitokausi (or
                             valittu-hoitokausi
                             [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                              (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi)))])
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        poikkeusvuosi? (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
        ;; Muokkaaminen on järjestelmävalvojalle aina sallittua, mutta muut on rajoitettu myös ajan perusteella
        voi-muokata? (or (roolit/jvh? @istunto/kayttaja)
                       (and
                         (onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
                         (not (onko-hoitokausi-tulevaisuudessa? valittu-hoitokausi nykyhetki))
                         (or
                           poikkeusvuosi?
                           (not (onko-hoitokausi-menneisyydessa? valittu-hoitokausi nykyhetki urakan-alkuvuosi)))))
        kattohinnan-oikaisu-mahdollinen? (and
                                           (seq hoitokauden-oikaisut)
                                           voi-muokata?
                                           (lupaus-domain/vuosi-19-20? urakan-alkuvuosi))]
    [:div
     [yhteiset/tavoitehinnan-oikaisut-taulukko hoitokauden-oikaisut-atom
      {:voi-muokata? voi-muokata? :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
       :poista-oikaisu-fn #(e! (valikatselmus-tiedot/->PoistaOikaisu %1 %2))
       :tallenna-oikaisu-fn #(e! (valikatselmus-tiedot/->TallennaOikaisu %1 %2))
       :paivita-oikaisu-fn #(e! (valikatselmus-tiedot/->PaivitaTavoitehinnanOikaisut %1 %2))}]
     (when (and paatoksia? voi-muokata?)
       [:div.oikaisu-paatos-varoitus
        [ikonit/harja-icon-status-alert]
        [:span "Hinnan oikaisun jälkeen joudut tallentamaan päätökset uudestaan"]])

     (when kattohinnan-oikaisu-mahdollinen?
       [kattohinnan-oikaisu e! app])]))

(defn urakalla-ei-tavoitehintaa-varoitus []
  [:div.tavoitehinta-tyhja-varoitus.margin-top-16
   [ikonit/livicon-warning-sign]
   [:span
    [:p
     [:strong
      "Hoitokaudelle ei ole asetettu tavoitehintaa!"]]
    [:p "Täytä tavoitehinta suunnitteluosiossa valitulle hoitokaudelle"]]])

(defn tavoitehinnan-ylitys-lomake [e! {:keys [hoitokauden-alkuvuosi urakan-paatokset]} toteuma
                                   oikaistu-tavoitehinta oikaistu-kattohinta voi-muokata?]
  (let [ylityksen-maara (if (> toteuma oikaistu-kattohinta)
                          (- oikaistu-kattohinta oikaistu-tavoitehinta)
                          (- toteuma oikaistu-tavoitehinta))
        urakoitsijan-osuus (* valikatselmus/+urakoitsijan-osuus-ylityksesta+ ylityksen-maara)
        tilaajan-osuus (- ylityksen-maara urakoitsijan-osuus)
        _ (js/console.log "tavoitehinnan-ylitys-lomake :: hoitokauden-alkuvuosi: " (pr-str hoitokauden-alkuvuosi))
        _ (js/console.log "tavoitehinnan-ylitys-lomake :: paatokset: " (pr-str urakan-paatokset))
        paatos (valikatselmus-tiedot/filtteroi-paatos
                 hoitokauden-alkuvuosi
                 ::valikatselmus/tavoitehinnan-ylitys
                 urakan-paatokset)
        _ (js/console.log "tavoitehinnan-ylitys-lomake :: filtteröity paatos: " (pr-str paatos))
        paatos-tehty? (some? paatos)
        paatoksen-tiedot (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                                 ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                 ::valikatselmus/urakoitsijan-maksu urakoitsijan-osuus
                                 ::valikatselmus/tilaajan-maksu tilaajan-osuus
                                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi}
                           (when (::valikatselmus/paatoksen-id paatos)
                             {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id paatos)}))]
    [:div.paatos
     [:div {:class ["paatos-check" (when-not paatos-tehty? "ei-tehty")]}
      [ikonit/livicon-check]]
     [:div.paatos-sisalto
      [:h3 (str "Tavoitehinnan ylitys " (fmt/euro-opt ylityksen-maara))]

      [:div.urakoitsijan-maksu
       [:p "Urakoitsija maksaa " [:strong (fmt/euro-opt urakoitsijan-osuus)] " (30 %)"]
       [:p.vihjeteksti "Kulu luodaan automaattisesti."]]
      [:div
       [:p "Tilaaja maksaa " [:strong (fmt/euro-opt tilaajan-osuus)] " (70 %)"]]

      ;; Näytetään joko tallenna- tai peru-nappi
      (if (not paatos-tehty?)
        [:<>
         (when (and voi-muokata? (or (zero? oikaistu-tavoitehinta) (nil? oikaistu-tavoitehinta)))
           [urakalla-ei-tavoitehintaa-varoitus])
         [napit/yleinen-ensisijainen "Tallenna päätös"
          #(e! (valikatselmus-tiedot/->TallennaPaatos paatoksen-tiedot))
          {:disabled (not voi-muokata?)}]]

        [napit/nappi
         "Kumoa päätös"
         #(e! (valikatselmus-tiedot/->PoistaPaatos (::valikatselmus/paatoksen-id paatos) ::valikatselmus/tavoitehinnan-alitus))
         {:disabled (not voi-muokata?)
          :luokka "nappi-toissijainen napiton-nappi"
          :ikoni [ikonit/harja-icon-action-undo]}])]]
    )
  )

(defn tavoitehinnan-alitus-lomake [e! {:keys [hoitokauden-alkuvuosi urakan-paatokset]} toteuma
                                   oikaistu-tavoitehinta tavoitehinta voi-muokata?]
  (let [alituksen-maara (- oikaistu-tavoitehinta toteuma)
        urakoitsijan-osuus (* valikatselmus/+tavoitepalkkio-kerroin+ alituksen-maara) ;; 30% alituksesta
        viimeinen-hoitokausi? (>= hoitokauden-alkuvuosi (dec (pvm/vuosi (:loppupvm @nav/valittu-urakka))))
        ;; Jos tavoitepalkkio on yli 3% tavoitehinnasta, yli jäävä osa siirretään.
        maksimi-tavoitepalkkio (if viimeinen-hoitokausi?
                                 urakoitsijan-osuus
                                 (* valikatselmus/+maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta))
        urakan-osuus-yli-maksimin? (< maksimi-tavoitepalkkio urakoitsijan-osuus)
        tavoitepalkkio (if urakan-osuus-yli-maksimin? maksimi-tavoitepalkkio urakoitsijan-osuus)
        maksimin-ylittava-summa (if urakan-osuus-yli-maksimin? (- urakoitsijan-osuus maksimi-tavoitepalkkio) 0)
        paatos (valikatselmus-tiedot/filtteroi-paatos
                 hoitokauden-alkuvuosi
                 ::valikatselmus/tavoitehinnan-alitus
                 urakan-paatokset)
        paatos-tehty? (some? paatos)
        paatos-id (::valikatselmus/paatoksen-id paatos)
        paatoksen-tiedot (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                                 ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-alitus
                                 ::valikatselmus/urakoitsijan-maksu (- tavoitepalkkio)
                                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                 ::valikatselmus/siirto (- maksimin-ylittava-summa)}
                           (when paatos-id
                             {::valikatselmus/paatoksen-id paatos-id}))
        lomakkeen-tila (cond
                         paatos-tehty? :paatos-tehty
                         urakan-osuus-yli-maksimin? :yli-maksimin
                         :oletus :ali-maksimin)]
    [:div.paatos
     [:div {:class ["paatos-check" (when-not paatos-tehty? "ei-tehty")]}
      [ikonit/livicon-check]]
     [:div.paatos-sisalto
      [:h3 (str "Tavoitehinnan alitus " (fmt/euro-opt alituksen-maara))]

      ;; Erotellaan lomakkeen tilat selkeästi
      (case lomakkeen-tila
        :ali-maksimin
        [:div "Urakoitsijalle maksetaan tavoitepalkkiona " [:strong (fmt/euro-opt tavoitepalkkio)] " (30 %)"]

        :yli-maksimin
        [:<>
         [:div.tavoitepalkkio-ylitys
          [ikonit/harja-icon-status-alert]
          [:span
           [:p "Urakoitsijan osuus on " [:strong (fmt/euro-opt urakoitsijan-osuus)] " (30 %)."]
           [:p (str "Tavoitepalkkion maksimimäärä (3% tavoitehinnasta) ylittyy "
                 (fmt/euro-opt false maksimin-ylittava-summa) " eurolla.")]]]
         [:div
          [:p "Urakoitsijalle maksetaan tavoitepalkkiona " [:strong (fmt/euro-opt tavoitepalkkio)]
           " (3% tavoitehinnasta)"]
          [:p "3% ylimenevä osuus " [:strong (fmt/euro-opt maksimin-ylittava-summa)]
           " siirretään seuraavan vuoden alennukseksi."]]]

        :paatos-tehty
        (let [maksettu-palkkio (- (::valikatselmus/urakoitsijan-maksu paatos))
              siirretty-summa (- (::valikatselmus/siirto paatos))]
          [:div
           [:p "Tavoitepalkkio tavoitehinnan alituksesta: " (fmt/euro-opt urakoitsijan-osuus)]
           [:ul
            (when (> maksettu-palkkio 0) [:li "Urakoitsijalle maksettava palkkio: " (fmt/euro-opt maksettu-palkkio)])
            (when (> siirretty-summa 0) [:li "Seuraavalle vuodelle siirtyvä lisäbudjetti: " (fmt/euro-opt siirretty-summa)])]]))

      ;; Näytetään joko tallenna- tai peru-nappi
      (if (not paatos-tehty?)
        [:<>
         (when (nil? tavoitehinta)
           [urakalla-ei-tavoitehintaa-varoitus])

         [napit/yleinen-ensisijainen "Tallenna päätös"
          #(e! (valikatselmus-tiedot/->TallennaPaatos paatoksen-tiedot))
          {:disabled (not voi-muokata?)}]]

        [napit/nappi
         "Kumoa päätös"
         #(e! (valikatselmus-tiedot/->PoistaPaatos (::valikatselmus/paatoksen-id paatos) ::valikatselmus/tavoitehinnan-alitus))
         {:disabled (not voi-muokata?)
          :luokka "nappi-toissijainen napiton-nappi"
          :ikoni [ikonit/harja-icon-action-undo]}])]]))

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
       [:h3 (str "Kattohinnan ylitys " (fmt/euro-opt ylityksen-maara))]
       (if voi-muokata?
         (if-not viimeinen-hoitokausi?
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
                :vaihtoehto-nayta {:maksu [:p "Urakoitsija maksaa hyvitystä " [:strong (fmt/euro-opt ylityksen-maara)] "(100 %)"]
                                   :siirto [:p "Ylitys " [:strong (fmt/euro-opt ylityksen-maara)] "siirretään seuraavan vuoden hankintakustannuksiin"]
                                   :osa "Osa siirretään ja osa maksetaan"}
                :oletusarvo :maksu}
               (r/wrap maksun-tyyppi
                 #(e! (valikatselmus-tiedot/->PaivitaMaksunTyyppi %)))])
            (if siirto-valittu?
              [:p.maksurivi "Siirretään ensi vuoden kustannuksiksi " [:strong (fmt/euro-opt siirto)]]
              [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/euro-opt maksettava-summa)] " (" (fmt/desimaaliluku-opt maksettava-summa-prosenttina) " %)"])]
           [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/euro-opt maksettava-summa)]])

         ;; FIXME: Ei figma-speksiä, korjaa kunhan sellainen löytyy.
         (if (::valikatselmus/paatoksen-id kattohinnan-ylitys-lomake)
           [:<>
            (when (pos? maksettava-summa)
              [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/euro-opt maksettava-summa)]])
            (when (pos? siirto)
              [:p.maksurivi "Urakoitsija maksaa hyvitystä " [:strong (fmt/euro-opt maksettava-summa)] " (" (fmt/desimaaliluku-opt maksettava-summa-prosenttina) " %)"])]
           [:p "Aluevastaava tekee päätöksen kattohinnan ylityksestä"]))

       (when voi-muokata?
         (if muokattava?
           [:<>
            (when (nil? tavoitehinta)
              [urakalla-ei-tavoitehintaa-varoitus])
            [napit/yleinen-ensisijainen "Tallenna päätös"
             #(e! (valikatselmus-tiedot/->TallennaPaatos paatoksen-tiedot))
             {:disabled (and osa-valittu? (seq (::lomake/virheet kattohinnan-ylitys-lomake)))}]]
           [napit/nappi
            "Kumoa päätös"
            #(e! (valikatselmus-tiedot/->PoistaPaatos (::valikatselmus/paatoksen-id kattohinnan-ylitys-lomake) ::valikatselmus/kattohinnan-ylitys))
            {:luokka "nappi-toissijainen napiton-nappi"
             :ikoni [ikonit/harja-icon-action-undo]}]))]]]))

(defn lupaus-lomake [e! oikaistu-tavoitehinta app voi-muokata?]
  (let [yhteenveto (:yhteenveto app)
        hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        paatos-tehty? (or (= :katselmoitu-toteuma (:ennusteen-tila yhteenveto)) false)
        luvatut-pisteet (get-in app [:lupaus-sitoutuminen :pisteet])
        toteutuneet-pisteet (get-in app [:yhteenveto :pisteet :toteuma])
        tavoitehinta (get-in app [:yhteenveto :tavoitehinta])
        lupausbonus (get-in app [:yhteenveto :bonus-tai-sanktio :bonus])
        lupaussanktio (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio])
        tavoite-taytetty? (get-in app [:yhteenveto :bonus-tai-sanktio :tavoite-taytetty])
        tallennus-kesken? (:tallennus-kesken? app)
        urakoitsijan-maksu (cond lupaussanktio lupaussanktio
                                 tavoite-taytetty? 0M
                                 :else nil)
        tilaajan-maksu (cond lupausbonus lupausbonus
                             tavoite-taytetty? 0M
                             :else nil)
        summa (cond
                lupaussanktio (- lupaussanktio)          ; Käännetään positiiviseksi, koska nyt maksetaan
                lupausbonus lupausbonus
                tavoite-taytetty? 0M)
        pisteet (get-in app [:yhteenveto :pisteet :toteuma])
        sitoutumis-pisteet (get-in app [:lupaus-sitoutuminen :pisteet])
        lupaus-tyyppi (if (or lupausbonus tavoite-taytetty?) ::valikatselmus/lupausbonus ::valikatselmus/lupaussanktio)
        lomake-avain (if (or lupausbonus tavoite-taytetty?) :lupausbonus-lomake :lupaussanktio-lomake)
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
         lupaussanktio
         [:h3 "Lupaukset: Urakoitsija maksaa sakkoa " (fmt/euro-opt summa) " luvatun pistemäärän alittamisesta."]
         lupausbonus
         [:h3 (str "Lupaukset: Urakoitsija saa bonusta " (fmt/euro-opt summa) " luvatun pistemäärän ylittämisestä.")]
         tavoite-taytetty?
         [:h3 (str "Lupaukset: Urakoitsija pääsi tavoitteeseen.")])
       [:p "Urakoitsija sai " pisteet " ja lupasi " sitoutumis-pisteet " pistettä." " Tavoitehinta: " (fmt/euro-opt tavoitehinta)]
       [:div {:style {:padding-top "22px"}}
        (cond
          (or lupausbonus lupaussanktio)
          [:<>
           (if lupaussanktio
             " Urakoitsija maksaa sanktiota "
             " Maksetaan urakoitsijalle bonusta ")
           [:strong (fmt/euro-opt summa)]
           (when (not (nil? (:indeksikorotus yhteenveto)))
             (str " (+ indeksi  " (fmt/euro-opt (if lupaussanktio
                                                  (* -1 (:indeksikorotus yhteenveto)) ;; Käännetään positiiviseksi
                                                  (:indeksikorotus yhteenveto)
                                                  )) " )"))]

          tavoite-taytetty?
          [:<>
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
                     (dissoc paatoksen-tiedot ::valikatselmus/paatoksen-id)))
              {:disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}]
             (if lupaussanktio
               [:p "Aluevastaava tekee päätöksen sanktion maksamisesta."]
               [:p "Aluevastaava tekee päätöksen bonuksen maksamisesta."]))]
          [:div {:style {:flex-grow 1}}
           (if on-oikeudet?
             [napit/nappi
              "Kumoa päätös"
              #(e! (valikatselmus-tiedot/->PoistaLupausPaatos paatos-id))
              {:luokka "nappi-toissijainen napiton-nappi"
               :ikoni [ikonit/harja-icon-action-undo]
               :disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}]
             (if lupaussanktio
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
        {:keys [tavoitehinta]} (t-yhteiset/hoitokauden-tavoitehinta hoitokausi-nro app)
        oikaistu-tavoitehinta (t-yhteiset/hoitokauden-oikaistu-tavoitehinta hoitokausi-nro app)
        oikaistu-kattohinta (t-yhteiset/hoitokauden-oikaistu-kattohinta hoitokausi-nro app)
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
        poikkeusvuosi? (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
        ;; Muokkaaminen on järjestelmävalvojalle aina sallittua, mutta muut on rajoitettu myös ajan perusteella
        voi-muokata? (or (roolit/jvh? @istunto/kayttaja)
                       (and
                         (onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
                         (not (onko-hoitokausi-tulevaisuudessa? valittu-hoitokausi nykyhetki))
                         (or
                           poikkeusvuosi?
                           (not (onko-hoitokausi-menneisyydessa? valittu-hoitokausi nykyhetki urakan-alkuvuosi))
                           )))]

    ;; Piilotetaan kaikki mahdollisuudet tehdä päätös, jos tavoitehintaa ei ole asetettu.
    (when (and oikaistu-tavoitehinta (> oikaistu-tavoitehinta 0))
      [:div
       [:h2 "Budjettiin liittyvät päätökset"]
       (when tavoitehinnan-ylitys?
         [tavoitehinnan-ylitys-lomake e! app toteuma oikaistu-tavoitehinta oikaistu-kattohinta voi-muokata?])
       (when kattohinnan-ylitys?
         [kattohinnan-ylitys-lomake e! app toteuma oikaistu-kattohinta tavoitehinta voi-muokata?])
       (when alitus?
         [tavoitehinnan-alitus-lomake e! app toteuma oikaistu-tavoitehinta tavoitehinta voi-muokata?])
       [:h2 "Lupauksiin liittyvät päätökset"]
       (if lupaukset-valmiina?
         [lupaus-lomake e! oikaistu-tavoitehinta app voi-muokata?]
         [lupaus-ilmoitus e! app])])))

(defn valikatselmus [e! app]
  (komp/luo
    (komp/sisaan #(do
                    (e! (lupaus-tiedot/->HaeUrakanLupaustiedot (:urakka @tila/yleiset)))
                    (e! (valikatselmus-tiedot/->NollaaPaatoksetJosUrakkaVaihtui))
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
