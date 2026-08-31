(ns harja.views.hallinta.tyokalut.laatupoikkeamasanktiotyokalu-nakyma
  "Työkalu laatupoikkeamien lähettämiseen APIn kautta."
  (:require [tuck.core :refer [tuck]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.hallinta.tyokalut.laatupoikkeamasanktiotyokalu-tiedot :as tiedot])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- tyyppi-valinnat [app]
  (tiedot/sanktiotyypit-lajille app (:sanktio app)))

(defn- voi-lahettaa? [sanktio]
  (and (:valittu-hallintayksikko sanktio)
    (:valittu-urakka sanktio)
    (:laji sanktio)
    (:paivamaara sanktio)
    (:kasittelyaika sanktio)
    (:maarattypvm sanktio)
    (:perintapvm sanktio)
    (:kohde sanktio)
    (:perustelu sanktio)
    (:kasittelytapa sanktio)
    (:tyyppi sanktio)
    (or (not (sanktio-domain/muu-kuin-muistutus? sanktio))
      (number? (:summa sanktio)))))

(defn- tieosoite-tekstina [tr-osoite]
  (when tr-osoite
    (str "Tie " (:tie tr-osoite)
      ", alkuosa " (:aosa tr-osoite)
      ", alkuetäisyys " (:aet tr-osoite)
      ", loppuosa " (:losa tr-osoite)
      ", loppuetäisyys " (:let tr-osoite))))

(defn- data-url->base64 [data-url]
  (second (re-find #"^data:.*;base64,(.*)$" data-url)))

(defn- lue-liite! [sanktio e! tiedosto]
  (let [lukija (js/FileReader.)]
    (set! (.-onload lukija)
      (fn [e]
        (let [data-url (-> e .-target .-result)
              sisalto (data-url->base64 data-url)
              liite {:liite {:nimi (.-name tiedosto)
                             :tyyppi (or (.-type tiedosto) "application/octet-stream")
                             :kuvaus "Liitetiedosto"
                             :sisalto sisalto}}]
          (if sisalto
            (e! (tiedot/->Muokkaa (assoc sanktio :liitteet [liite])))
            (viesti/nayta-toast! "Liitteen lukeminen epäonnistui" :varoitus)))))
    (set! (.-onerror lukija)
      (fn [_]
        (viesti/nayta-toast! "Liitteen lukeminen epäonnistui" :varoitus)))
    (.readAsDataURL lukija tiedosto)))

(defn sanktiolomake [e! {:keys [sanktio] :as app}]
  (let [tyypit (tyyppi-valinnat app)
        voi-lahettaa? (voi-lahettaa? sanktio)
        liite (first (:liitteet sanktio))]
    [:div.yhteydenpito
     [:h3 "Sanktiotyökalu"]
     [:p "Lähetä laatupoikkeama. Lomake ei vielä tue sanktioita tai bonuksia tai arvonvähennyksiä. Vain laatupoikkeamia. API ei tue vielä muita."]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [sanktio]
                    [:div
                     [napit/tallenna "Hae tieosoite koordinaateista"
                      #(e! (tiedot/->HaeTieosoite))
                      {:disabled (:tieosoitteen-haku-kaynnissa? app)}]
                     [napit/tallenna "Lähetä"
                      #(e! (tiedot/->Laheta sanktio))
                      {:disabled (not voi-lahettaa?)
                       :paksu? true}]])
       :muokkaa! #(e! (tiedot/->Muokkaa %))}
      [{:nimi :valittu-hallintayksikko
        :otsikko "Valitse hallintayksikkö"
        :tyyppi :valinta
        :valinnat @hal/vaylamuodon-hallintayksikot
        :valinta-nayta :nimi
        :pakollinen? true}
       {:id (hash (:mahdolliset-urakat app))
        :nimi :valittu-urakka
        :otsikko "Valitse urakka"
        :tyyppi :valinta
        :valinnat (:mahdolliset-urakat app)
        :valinta-nayta :nimi
        :pakollinen? true}
       {:nimi :sanktiomuoto
        :otsikko "Sanktion muoto"
        :tyyppi :radio-group
        :vaihtoehdot [:suorasanktio :laatupoikkeaman-sanktio]
        :vaihtoehto-nayta (fn [arvo]
                            ({:suorasanktio "Suorasanktio"
                              :laatupoikkeaman-sanktio "Laatupoikkeaman normaali sanktio"}
                             arvo))}
       {:nimi :laji
        :otsikko "Sanktion laji"
        :tyyppi :valinta
        :valinnat tiedot/+sanktiolajit+
        :valinta-nayta #(or (sanktio-domain/sanktiolaji->teksti %) "Valitse laji")
        :pakollinen? true}
       {:nimi :tyyppi
        :otsikko "Sanktiotyyppi"
        :tyyppi :valinta
        :valinta-arvo identity
        :valinnat tyypit
        :valinta-nayta #(or (sanktio-domain/sanktiotyypin-nimi
                              (sanktio-domain/sanktiolaji->teksti (:laji sanktio))
                              %)
                          "Valitse sanktiotyyppi")
        :pakollinen? true}
       {:nimi :summa
        :otsikko "Sanktion suuruus"
        :tyyppi :numero
        :pakollinen? (sanktio-domain/muu-kuin-muistutus? sanktio)
        :napiton? (not (sanktio-domain/muu-kuin-muistutus? sanktio))}
       {:nimi :paivamaara
        :otsikko "Havaittu"
        :tyyppi :pvm
        :pakollinen? true}
       {:nimi :kasittelyaika
        :otsikko "Käsitelty"
        :tyyppi :pvm
        :pakollinen? true}
       {:nimi :maarattypvm
        :otsikko "Määrätty"
        :tyyppi :pvm
        :pakollinen? true}
       {:nimi :perintapvm
        :otsikko "Perintäpäivä"
        :tyyppi :pvm
        :pakollinen? true}
       {:nimi :alku-x
        :otsikko "Alkusijainti x"
        :tyyppi :numero}
       {:nimi :alku-y
        :otsikko "Alkusijainti y"
        :tyyppi :numero}
       {:nimi :loppu-x
        :otsikko "Loppusijainti x (valinnainen)"
        :tyyppi :numero}
       {:nimi :loppu-y
        :otsikko "Loppusijainti y (valinnainen)"
        :tyyppi :numero}
       {:nimi :kasittelytapa
        :otsikko "Käsittelytapa"
        :tyyppi :valinta
        :valinnat sanktio-domain/kasittelytavat
        :valinta-nayta #(or (sanktio-domain/kasittelytapa->teksti %) "Valitse käsittelytapa")
        :pakollinen? true}
       {:nimi :kohde
        :otsikko "Kohde"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :kuvaus
        :otsikko "Kuvaus"
        :tyyppi :text
        :koko [60 6]}
       {:nimi :perustelu
        :otsikko "Perustelu"
        :tyyppi :text
        :koko [60 6]
        :pakollinen? true}]
      sanktio]
     [:div.form-group
      [:label.control-label
       [:span.kentan-label "Liitetiedosto (valinnainen) - HOX! asetukset.edn:stä pitää vaihtaa virustarkistus url nilliksi, jotta tämä voi toimia lokaalisti"]]
      [:input {:type "file"
               :class "form-control"
               :on-change #(let [tiedosto (aget (.. % -target -files) 0)]
                             (when tiedosto
                               (lue-liite! sanktio e! tiedosto)))}]
      (when liite
        [:div {:style {:margin-top "8px"}}
         [:span (str "Valittu liite: " (get-in liite [:liite :nimi]))]
         [napit/yleinen-toissijainen
          "Poista liite"
          #(e! (tiedot/->Muokkaa (assoc sanktio :liitteet [])))
          {:luokka "margin-left-16"}]])]
     (when (:haettu-tr-osoite app)
       [:p [:b "Haettu tieosoite: "] (tieosoite-tekstina (:haettu-tr-osoite app))])
     [debug/debug app]]))

(defn laheta-sanktio* []
  (komp/luo
    (komp/sisaan-ulos
      #(go
         (reset! tiedot/nakymassa? true)
         (reset! tiedot/data tiedot/alkutila))
      #(reset! tiedot/nakymassa? false))
    (fn [e! app]
      (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
        (when @tiedot/nakymassa?
          (when (and (empty? (:sanktiotyypit app))
                  (not (:sanktiotyypit-haku-kaynnissa? app)))
            (e! (tiedot/->HaeSanktiotyypit)))
          (sanktiolomake e! app))
        "Puutteelliset käyttöoikeudet"))))

(defn laheta-sanktio []
  [tuck tiedot/data laheta-sanktio*])
