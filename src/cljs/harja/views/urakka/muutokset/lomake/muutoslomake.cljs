(ns harja.views.urakka.muutokset.lomake.muutoslomake
  "Muutokset välilehden lomakkeet (Lisäys / Muokkaus)"
  (:require
    [taoensso.timbre :as log]
    [harja.tiedot.urakka.muutokset.kirjatut-muutokset-tiedot :as t-kirjatut]
    [harja.ui.napit :as napit]
    [harja.ui.lomake :as lomake]
    [harja.ui.komponentti :as komp]
    [harja.tiedot.navigaatio :as nav]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.domain.muutos-domain :as muutos-domain]
    [harja.views.urakka.muutokset.yhteiset :as yhteiset]
    [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
    [harja.ui.yleiset :as yleiset]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]

    ;; Lomake tyypit, näitä voi lisäillä tarvittaessa
    [harja.views.urakka.muutokset.lomake.lomake-pysyva :as pysyva]
    [harja.views.urakka.muutokset.lomake.lomake-johto-hallinto :as johto-ja-hallinto]
    [harja.views.urakka.muutokset.lomake.lomake-muutostyo :as muutostyo]))


(defn poista-muutos-varmistus
  [e! muutos]
  (let [vahvistus-tiedot (cond
                           ;; JJH-muutos
                           (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
                           {:sisalto [:div "Muutoksen poistaminen poistaa myös Harjan automaattisesti luomat maksuerät."]}

                           ;; Erillisrahoitettu muutostyö
                           (and (= (:tyyppi muutos) "muutostyo")
                             (= (:alityyppi muutos) :erillisrahoitus))
                           {:sisalto [:div "Muutostyölle ei voi poistamisen jälkeen kohdistaa kuluja"]}

                           ;; Pysyvä muutos, muu muutostyö ja kaikki muut mahdolliset muutostyypit (default)
                           :else
                           {:sisalto [:div "Haluatko varmasti poistaa muutoksen?"]})]

    ;; Näytä vahvistusdialogi
    (varmista-kayttajalta/varmista-kayttajalta
      (merge {:otsikko "Poistetaanko muutos?"
              :hyvaksy "Poista"
              :toiminto-fn (fn [] (e! (t-yhteiset/->PoistaMuutos muutos)))}
        vahvistus-tiedot))))

(defn- lomakkeen-footer [muutos tyyppi e!
                         {:keys [tallennus-kesken? voi-tallentaa?
                                 tallenna-painettu? lomakkeella-virheita?
                                 lomake-virheet muutoksen-tiedot-haku-kaynnissa?] :as _app}]
  [:<>
   [:hr]
   (when (and
           tallenna-painettu?
           (seq lomake-virheet))
     [yleiset/nayta-virheet :varoitus lomake-virheet])

   ;; Muutostyö lomakkeeseen design mukaan infolaatikko
   (when (= tyyppi "muutostyo")
     [yleiset/info-laatikko :neutraali
      "Tallentamisen jälkeen muutostyölle voi kohdistaa kuluja."
      nil nil
      {:luokka "perustiedot"}])

   [:div.muutoslomake-footer-toiminnot
    [napit/tallenna "Tallenna"
     #(do
        (t-yhteiset/scrollaa-viimeksi-valitulle-riville)
        (tuck-apurit/e-kanavalla! e! t-yhteiset/->TallennaMuutos muutos))
     ;; Saavutettavuusmielessä, halutaan näyttää virheet vasta, kun tallenna nappia painettu 
     ;; Tallenna nappi on myös disabled alkutilassa, kun lomaketta ei ole muokattu 
     {:disabled (if (or
                      tallennus-kesken?
                      (not voi-tallentaa?)
                      muutoksen-tiedot-haku-kaynnissa?)
                  true
                  false)}]

    [napit/peruuta "Peruuta"
     #(do
        (t-yhteiset/scrollaa-viimeksi-valitulle-riville)
        (e! (t-yhteiset/->MuokkaaMuutosta nil)))
     {:disabled tallennus-kesken?}]

    (when (:id muutos)
      ;; TODO: Tehdään tästä erillisessä PR:ssä yleinen pattern, joka korvaa vanhentuneen napit/poista komponentin.
      ;;       Figman Design libraryn mukaisessa tyylissä ei ole lainkaan vanhentunutta punaista napit/poista nappia, vaan
      ;;       tämä uusi toissijainen nappi, joka on tyyliltään hillitty, kaikille toissijaisille toiminnoille sopiva nappi.
      [napit/yleinen-toissijainen
       "Poista muutos"
       #(poista-muutos-varmistus e! muutos)
       {:ikoni [ikonit/livicon-trash] :paksu? true
        :disabled (or
                    tallennus-kesken?
                    muutoksen-tiedot-haku-kaynnissa?)}])

    (when (or
            tallennus-kesken?
            muutoksen-tiedot-haku-kaynnissa?)
      [yleiset/ajax-loader-pieni "Ladataan..."])]])

(defn- alusta-lomakkeen-pohjatiedot [e! muutostyyppi valittu-hoitokausi rivi]
  (log/debug "Haetaan lomakkeen pohjatiedot muutostyypille:" muutostyyppi)

  (let [rivi (case muutostyyppi
               "pysyva" (do
                          (e! (t-kirjatut/->HaePysyvanMuutoksenPohjatiedotLomakkeelle))
                          rivi)
               ;; TODO: Tämä on jäänne muutosten ensimmäisen version ajalta, ja poikkeaa muusta logiikasta
               ;;       Vaatisi refaktorointia, jotta logiikka lomakkeen alustamiselle olisi yhtenäisempi
               "johto-ja-hallintokorvaus" (assoc rivi
                                            :johto-ja-hallintokorvaukset
                                            (t-yhteiset/johto-ja-hallintokorvausmuutoksen-rivit valittu-hoitokausi []))
               ;; Asetetaan default arvo erillisrahoitetulle muutostyölle
               ;; TODO: Normaalisti default-arvo tulisi lomake_muutostyo.cljs:ssä oletusarvona alityypin radio-group valinnasta
               ;;       Mutta, koska alityypin valinta on toistaiseksi poistettu käytöstä, asetetaan se tässä default-arvoksi
               ;;       Poista tämä, kun alityypin valinta otetaan takaisin käyttöön muutostyo-lomakkeessa.
               "muutostyo" (assoc rivi
                             :alityyppi :erillisrahoitus)
               rivi)]
    rivi))

(defn- lomakkeen-tyyppivalinta
  [e! {:keys [valittu-hoitokausi] :as app}]
  (let [valittu-tyyppi (get-in app [:muokattava-muutos :tyyppi])]
    (vec
      (keep identity
        (concat
          [{:otsikko "Tyyppi"
            :nimi :tyyppi
            :pakollinen? true
            :aseta (fn [rivi arvo]
                     (->>
                       ;; Aseta valittu tyyppi
                       (assoc rivi :tyyppi arvo)
                       ;; Haetaan pohjatietoja tietyille lomaketyypeille, kun uutta muutosta luodaan
                       (alusta-lomakkeen-pohjatiedot e! arvo valittu-hoitokausi)))
            ;; Sallitaan muokkaus vain uudelle muutokselle
            :muokattava? #(nil? (:id %))
            :kaariva-luokka "muutostyyppivalinta"
            :tyyppi :valinta
            :vayla-tyyli? true
            :valinnat muutos-domain/+muutostyypit-lomakkeella+
            :valinta-arvo identity
            :valinta-nayta (fn [arvo]
                             (muutos-domain/tyyppi-fmt arvo (:sopimustyyppi @nav/valittu-urakka)))
            :uusi-rivi? true
            ::lomake/col-luokka "perustiedot col-sm-6"}]
          (when (contains? #{"pysyva" "muutostyo" "johto-ja-hallintokorvaus"} valittu-tyyppi)
            [{:nimi :teksti
              :tyyppi :komponentti
              :kaariva-luokka "laskutusraja-infoteksti"
              :komponentti (fn []
                             [:div
                              (case valittu-tyyppi
                                "pysyva"
                                [:p "Tämä muutostyö otetaan huomioon hoitovuoden laskutusrajan laskennassa vain 1. voimassaolovuoden
                                osalta ja jos voimassaolovuoden tavoitehinnan muutos on positiivinen (plusmerkkinen)."]

                                "muutostyo"
                                [:p "Tämä muutostyö otetaan huomioon hoitovuoden laskutusrajan laskennassa."]

                                "johto-ja-hallintokorvaus"
                                [:p "Tämä muutostyö ei vaikuta hoitovuoden laskutusrajan laskentaan."])
                              [:hr]])}]))))))


(defn muutoslomake [e! {:keys [muokattava-muutos muutoksen-tiedot-haku-kaynnissa?] :as _app}]
  (komp/luo
    (komp/sisaan-ulos
      #(e! (t-yhteiset/->HaeMuutoksenTiedot muokattava-muutos))
      #(e! (t-yhteiset/->MuokkaaMuutosta nil)))

    (fn [e! {:keys [muokattava-muutos muutoksen-tiedot-haku-kaynnissa?] :as app}]
      [:div.muutoslomake
       [lomake/lomake
        {:otsikko [:div.flex-row.alkuun
                   (if (:id muokattava-muutos) "Muokkaa muutosta" "Lisää uusi muutos")
                   (when muutoksen-tiedot-haku-kaynnissa?
                     [yleiset/ajax-loader-pieni])]
         :tarkkaile-ulkopuolisia-muutoksia? true
         :muokkaa! #(e! (t-yhteiset/->PaivitaLomake %))
         :footer-fn (fn [muutos] (lomakkeen-footer muutos (:tyyppi muokattava-muutos) e! app))
         :voi-muokata? (not muutoksen-tiedot-haku-kaynnissa?)}

        ;; Tähän lomakkeiden muutostyyppikohtaiset skeemat
        (into []
          (concat
            (lomakkeen-tyyppivalinta e! app)

            (case (:tyyppi muokattava-muutos)
              "erillisrahoitettu" (yhteiset/lomake-yhteinen e! app)
              "johto-ja-hallintokorvaus" (johto-ja-hallinto/lomake-johto-ja-hallintokorvaus e! app)
              "maarapoikkeama" (yhteiset/lomake-yhteinen e! app)
              "pysyva" (pysyva/lomake-pysyva e! app)
              "toteutuneet-maarat" (yhteiset/lomake-yhteinen e! app)
              "muutostyo" (muutostyo/lomake-muutostyo e! app)

              nil [(lomake/ryhma {:otsikko "Valitse tyyppi"})]

              ;; Default - jos mikään ylläolevista ei osu
              [(lomake/ryhma {:otsikko "Sisältöä ei saatavilla."})])))
        muokattava-muutos]])))
