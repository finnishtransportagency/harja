(ns harja.tiedot.urakka.lupaus-tiedot
  "Urakan lupausten tiedot."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.asiakas.kommunikaatio :as kommunikaatio]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset]
            [harja.domain.lupaus-domain :as lupaus-domain])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Hae lupaustiedot
(defrecord HaeUrakanLupaustiedot [urakka])
(defrecord HaeUrakanLupaustiedotOnnnistui [vastaus])
(defrecord HaeUrakanLupaustiedotEpaonnistui [vastaus])

;; Kommentit
(defrecord HaeKommentitOnnistui [vastaus])
(defrecord HaeKommentitEpaonnistui [vastaus])
(defrecord LisaaKommentti [kommentti])
(defrecord LisaaKommenttiOnnistui [vastaus])
(defrecord LisaaKommenttiEpaonnistui [vastaus])
(defrecord PoistaKommentti [id])
(defrecord PoistaKommenttiOnnistui [vastaus])
(defrecord PoistaKommenttiEpaonnistui [vastaus])

;; Vaihda hoitokausi
(defrecord HoitokausiVaihdettu [urakka hoitokausi])

;; Sitoutuminen
(defrecord VaihdaLuvattujenPisteidenMuokkausTila [])
(defrecord LuvattujaPisteitaMuokattu [pisteet])
(defrecord TallennaLupausSitoutuminen [urakka])
(defrecord TallennaLupausSitoutuminenOnnnistui [vastaus])
(defrecord TallennaLupausSitoutuminenEpaonnistui [vastaus])

;; Sivupaneeli
(defrecord AvaaLupausvastaus [vastaus kuukausi kohdevuosi])
(defrecord SuljeLupausvastaus [])
(defrecord ValitseVastausKuukausi [kuukausi vuosi])
(defrecord ValitseVaihtoehto [vaihtoehto lupaus kohdekuukausi kohdevuosi])
(defrecord ValitseVaihtoehtoOnnistui [vastaus])
(defrecord ValitseVaihtoehtoEpaonnistui [vastaus])

(defrecord ValitseKE [vastaus lupaus kohdekuukausi kohdevuosi])
(defrecord ValitseKEOnnistui [vastaus])
(defrecord ValitseKEEpaonnistui [vastaus])

;; Päänäkymä ja listaus
(defrecord AvaaLupausryhma [kirjain])
(defrecord ValitseUrakka [urakka])
(defrecord NakymastaPoistuttiin [])

;; Kuukausipisteet
(defrecord TallennaKuukausipisteet [pisteet-id kohdekuukausi kohdevuosi tyyppi urakka])
(defrecord TallennaKuukausipisteetOnnistui [vastaus])
(defrecord TallennaKuukausipisteetEpaonnistui [vastaus])
(defrecord Kuukausipisteitamuokattu [pisteet kuukausi])
(defrecord AvaaKuukausipisteetMuokattavaksi [kuukausi])

;; Testaus
(defrecord AsetaNykyhetki [nykyhetki])

(defn valitse-urakka [app urakka]
  (let [hoitokaudet (u/hoito-tai-sopimuskaudet urakka)
        vanha-hoitokausi (:valittu-hoitokausi app)
        uusi-hoitokausi (if (contains? (set hoitokaudet) vanha-hoitokausi)
                          vanha-hoitokausi
                          (u/paattele-valittu-hoitokausi hoitokaudet))]
    ;; Tyhjennä muu app state kun urakka vaihtuu
    (-> {}
        (assoc :urakan-hoitokaudet hoitokaudet)
        (assoc :valittu-hoitokausi uusi-hoitokausi))))

(defn- lupausten-hakuparametrit [urakka hoitokausi nykyhetki]
  (merge
    {:urakka-id (:id urakka)
     :urakan-alkuvuosi (pvm/vuosi (:alkupvm urakka))
     :valittu-hoitokausi hoitokausi}
    (when nykyhetki
      ;; Palvelin sallii nykyhetken määrittämisen testausta varten, jos ei olla tuotantoympäristössä
      {:nykyhetki nykyhetki})))

(defn hae-urakan-lupaustiedot
  "Vuonna 2021 alkaville urakoille haetaan lupaustiedot. Sitä vanhemmille ei haeta."
  ([app] (hae-urakan-lupaustiedot app (:urakka @tila/yleiset)))
  ([app urakka]
   (let [vanha-urakka? (lupaus-domain/urakka-19-20? urakka)
         hakuparametrit (lupausten-hakuparametrit
                          urakka
                          (:valittu-hoitokausi app)
                          (:nykyhetki app))]
     (if-not vanha-urakka?
       ;; 2021 alkuiset urakat
       (tuck-apurit/post! :hae-urakan-lupaustiedot
                          hakuparametrit
                          {:onnistui ->HaeUrakanLupaustiedotOnnnistui
                           :epaonnistui ->HaeUrakanLupaustiedotEpaonnistui})

       ;; 2019/2020
       (tuck-apurit/post! :hae-kuukausittaiset-pisteet
                          hakuparametrit
                          {:onnistui ->HaeUrakanLupaustiedotOnnnistui
                           :epaonnistui ->HaeUrakanLupaustiedotEpaonnistui}))
     app)))

(defn hae-kommentit [{:keys [valittu-hoitokausi] :as app}]
  (if valittu-hoitokausi
    (let [app (assoc-in app [:kommentit :haku-kaynnissa?] true)]
      (tuck-apurit/post!
        app
        :lupauksen-kommentit
        {:lupaus-id (get-in app [:vastaus-lomake :lupaus-id])
         :urakka-id (-> @tila/tila :yleiset :urakka :id)
         :aikavali valittu-hoitokausi}
        {:onnistui ->HaeKommentitOnnistui
         :epaonnistui ->HaeKommentitEpaonnistui})
      app)
    app))

(defn valitse-vastauskuukausi [app kuukausi vuosi]
  (-> app
      (assoc-in [:vastaus-lomake :vastauskuukausi] kuukausi)
      (assoc-in [:vastaus-lomake :vastausvuosi] vuosi)
      (update :vastaus-lomake dissoc :lahetetty-vastaus)
      (hae-kommentit)))

(defn- tallenna-sitoutuminen [app urakka tulos! virhe!]
  (let [parametrit (merge (lupausten-hakuparametrit urakka (:valittu-hoitokausi app) (:nykyhetki app))
                          {:id (get-in app [:lupaus-sitoutuminen :id])
                           :pisteet (get-in app [:lupaus-sitoutuminen :pisteet])})]
    (go
      (let [vastaus (<! (kommunikaatio/post! :tallenna-luvatut-pisteet parametrit))]
        (if (kommunikaatio/virhe? vastaus)
          (virhe!)
          (tulos!))))
    app))

(extend-protocol tuck/Event
  HoitokausiVaihdettu
  (process-event [{urakka :urakka hoitokausi :hoitokausi} app]
    (let [app (-> app
                  (assoc :valittu-hoitokausi hoitokausi)
                  (dissoc :kommentit)
                  (dissoc :lupausryhmat)
                  (dissoc :yhteenveto))]
      (hae-urakan-lupaustiedot app urakka)
      app))

  HaeUrakanLupaustiedot
  (process-event [{urakka :urakka} app]
    (let [;; Lupauksia voidaan hakea myös välikatselmuksesta, niin tarkistetaan hoitokauden tila sitä ennen
          app (if (:valittu-hoitokausi app)
                app
                (assoc app :valittu-hoitokausi [(pvm/hoitokauden-alkupvm (:hoitokauden-alkuvuosi app))
                                                (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc (:hoitokauden-alkuvuosi app))))]))]
      (do
        (hae-urakan-lupaustiedot app urakka)
        app)))

  HaeUrakanLupaustiedotOnnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Haetaan lomakkeelle uudistuneet tiedot
    (let [lupaus (:vastaus-lomake app)
          lupaus-id (:lupaus-id lupaus)
          uusi-lupaus (lupaus-domain/etsi-lupaus vastaus lupaus-id)]
      (-> app
          (merge vastaus)
          (update :vastaus-lomake dissoc :lahetetty-vastaus)
          (update :vastaus-lomake merge uusi-lupaus))))

  HaeUrakanLupaustiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Lupaustietojen hakeminen epäonnistui!" :varoitus)
    (update app :vastaus-lomake dissoc :lahetetty-vastaus))

  HaeKommentitOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [lupaus-id (some-> vastaus first :lupaus-id)
          lupaus->kuukausi->kommentit (when lupaus-id
                                        {lupaus-id (group-by :kuukausi vastaus)})]
      (-> app
          (assoc-in [:kommentit :haku-kaynnissa?] false)
          (assoc-in [:kommentit :lisays-kaynnissa?] false)
          (assoc-in [:kommentit :poisto-kaynnissa?] false)
          (update-in [:kommentit :lupaus->kuukausi->kommentit] merge lupaus->kuukausi->kommentit))))

  HaeKommentitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Kommenttien hakeminen epäonnistui!" :varoitus)
    (-> app
        (assoc-in [:kommentit :haku-kaynnissa?] false)
        (assoc-in [:kommentit :lisays-kaynnissa?] false)
        (assoc-in [:kommentit :poisto-kaynnissa?] false)))

  LisaaKommentti
  (process-event [{kommentti :kommentti} app]
    (let [tiedot {:lupaus-id (get-in app [:vastaus-lomake :lupaus-id])
                  :urakka-id (-> @tila/tila :yleiset :urakka :id)
                  :kuukausi (get-in app [:vastaus-lomake :vastauskuukausi])
                  :vuosi (get-in app [:vastaus-lomake :vastausvuosi])
                  :kommentti kommentti}]
      (-> app
          (assoc-in [:kommentit :lisays-kaynnissa?] true)
          (tuck-apurit/post! :lisaa-lupauksen-kommentti
                             tiedot
                             {:onnistui ->LisaaKommenttiOnnistui
                              :epaonnistui ->LisaaKommenttiEpaonnistui}))))

  LisaaKommenttiOnnistui
  (process-event [{vastaus :vastaus} app]
    (hae-kommentit app))

  LisaaKommenttiEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Kommentin tallentaminen epäonnistui!" :varoitus)
    (-> app
        (assoc-in [:kommentit :lisays-kaynnissa?] false)))

  PoistaKommentti
  (process-event [{id :id} app]
    (-> app
        (assoc-in [:kommentit :poisto-kaynnissa?] true)
        (tuck-apurit/post! :poista-lupauksen-kommentti
                           {:id id}
                           {:onnistui ->PoistaKommenttiOnnistui
                            :epaonnistui ->PoistaKommenttiEpaonnistui})))

  PoistaKommenttiOnnistui
  (process-event [{vastaus :vastaus} app]
    (hae-kommentit app))

  PoistaKommenttiEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Kommentin poistaminen epäonnistui!" :varoitus)
    (-> app
        (assoc-in [:kommentit :poisto-kaynnissa?] false)))


  VaihdaLuvattujenPisteidenMuokkausTila
  (process-event [_ app]
    (let [arvo-nyt (:muokkaa-luvattuja-pisteita? app)]
      (assoc app :muokkaa-luvattuja-pisteita? (not arvo-nyt))))

  LuvattujaPisteitaMuokattu
  (process-event [{pisteet :pisteet} app]
    (assoc-in app [:lupaus-sitoutuminen :pisteet] pisteet))

  TallennaLupausSitoutuminen
  (process-event [{urakka :urakka} app]
    (let [;; Alustetaan tuck funktiot, jotta niitä voidaan kutsua process-eventin ulkopuolelta viiveellä.
          tulos! (tuck/send-async! ->TallennaLupausSitoutuminenOnnnistui)
          virhe! (tuck/send-async! ->TallennaLupausSitoutuminenEpaonnistui)]
      (do
        (yleiset/fn-viiveella #(tallenna-sitoutuminen app urakka tulos! virhe!) 200)
        app)))

  TallennaLupausSitoutuminenOnnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (hae-urakan-lupaustiedot app (-> @tila/tila :yleiset :urakka))
      (-> app
          (merge vastaus)
          (assoc :muokkaa-luvattuja-pisteita? false))))

  TallennaLupausSitoutuminenEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast!
      "Pisteiden tallennus epäonnistui"
      :varoitus
      viesti/viestin-nayttoaika-aareton)
    app)

  AvaaLupausvastaus
  (process-event [{vastaus :vastaus kuukausi :kuukausi vuosi :kohdevuosi} app]
    ;; Avataan sivupaneeli, lisätään vastauksen tiedot :vastaus-lomake avaimeen
    (-> app
        (assoc :vastaus-lomake vastaus)
        (assoc-in [:vastaus-lomake :vastauskuukausi] kuukausi)
        (assoc-in [:vastaus-lomake :vastausvuosi] vuosi)
        (valitse-vastauskuukausi kuukausi vuosi)))

  SuljeLupausvastaus
  (process-event [_ app]
    ;; Suljetaan sivupaneeli
    (dissoc app :vastaus-lomake))

  ValitseVastausKuukausi
  (process-event [{kuukausi :kuukausi vuosi :vuosi} app]
    (valitse-vastauskuukausi app kuukausi vuosi))

  AvaaLupausryhma
  (process-event [{kirjain :kirjain} app]
    (let [avoimet-lupausryhmat (if (:avoimet-lupausryhmat app)
                                 (into #{} (:avoimet-lupausryhmat app))
                                 #{})
          avoimet-lupausryhmat (if (contains? avoimet-lupausryhmat kirjain)
                                 (into #{} (disj avoimet-lupausryhmat kirjain))
                                 (into #{} (cons kirjain avoimet-lupausryhmat)))]
      (assoc app :avoimet-lupausryhmat avoimet-lupausryhmat)))

  ValitseUrakka
  (process-event [{urakka :urakka} app]
    (valitse-urakka app urakka))

  NakymastaPoistuttiin
  (process-event [_ app]
    app)

  ValitseVaihtoehto
  (process-event [{vaihtoehto :vaihtoehto lupaus :lupaus kohdekuukausi :kohdekuukausi kohdevuosi :kohdevuosi} app]
    (let [vastaus (merge
                    ;; Lisätään vastauksen id palvelupyyntöön vastauksen editoimiseksi, jos sellainen on tarjolla
                    (when (:kuukauden-vastaus-id vaihtoehto)
                      {:id (:kuukauden-vastaus-id vaihtoehto)})
                    {:lupaus-id (:lupaus-id lupaus)
                     :urakka-id (-> @tila/tila :yleiset :urakka :id)
                     :kuukausi kohdekuukausi
                     :vuosi kohdevuosi
                     :paatos (if (or (= kohdekuukausi (:paatos-kk lupaus)) (= 0 (:paatos-kk lupaus)))
                               true false)
                     :vastaus nil
                     :lupaus-vaihtoehto-id (:id vaihtoehto)})]
      (tuck-apurit/post! :vastaa-lupaukseen
                         vastaus
                         {:onnistui ->ValitseVaihtoehtoOnnistui
                          :epaonnistui ->ValitseVaihtoehtoEpaonnistui})
      (assoc-in app [:vastaus-lomake :lahetetty-vastaus] vastaus)))

  ValitseVaihtoehtoOnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Koska vastauksen antaminen muuttaa sekä vastauslomaketta, että vastauslistaa, niin haetaan koko setti uusiksi
    (hae-urakan-lupaustiedot app (-> @tila/tila :yleiset :urakka))
    app)

  ValitseVaihtoehtoEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Vastauksen antaminen epäonnistui!" :varoitus)
    (update app :vastaus-lomake dissoc :lahetetty-vastaus))

  ValitseKE
  (process-event [{vastaus :vastaus lupaus :lupaus kohdekuukausi :kohdekuukausi kohdevuosi :kohdevuosi} app]
    (let [vastaus-map (merge (when (:kuukauden-vastaus-id vastaus)
                               {:id (:kuukauden-vastaus-id vastaus)})
                             {:lupaus-id (:lupaus-id lupaus)
                              :urakka-id (-> @tila/tila :yleiset :urakka :id)
                              :kuukausi kohdekuukausi
                              :vuosi kohdevuosi
                              :paatos (if (or (= kohdekuukausi (:paatos-kk lupaus))
                                              (= 0 (:paatos-kk lupaus)))
                                        true false)
                              :vastaus (:vastaus vastaus)
                              :lupaus-vaihtoehto-id nil})]
      (tuck-apurit/post! :vastaa-lupaukseen
                         vastaus-map
                         {:onnistui ->ValitseKEOnnistui
                          :epaonnistui ->ValitseKEEpaonnistui})
      (assoc-in app [:vastaus-lomake :lahetetty-vastaus] vastaus-map)))

  ValitseKEOnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Koska vastauksen antaminen muuttaa sekä vastauslomaketta, että vastauslistaa, niin haetaan koko setti uusiksi
    (hae-urakan-lupaustiedot app (-> @tila/tila :yleiset :urakka))
    app)

  ValitseKEEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Vastauksen antaminen epäonnistui!" :varoitus)
    (update app :vastaus-lomake dissoc :lahetetty-vastaus))

  Kuukausipisteitamuokattu
  (process-event [{pisteet :pisteet kuukausi :kuukausi} app]
    (-> app
        (dissoc :kuukausipisteet-ehdotus)
        (assoc-in [:kuukausipisteet-ehdotus (keyword (str kuukausi))] pisteet)))

  TallennaKuukausipisteet
  (process-event [{pisteet-id :pisteet-id kohdekuukausi :kohdekuukausi kohdevuosi :kohdevuosi
                   tyyppi :tyyppi urakka :urakka} app]
    (let [pisteet (get-in app [:kuukausipisteet-ehdotus (keyword (str kohdekuukausi))])
          parametrit (merge
                       {:urakka-id (:id urakka) :kuukausi kohdekuukausi :vuosi kohdevuosi :pisteet pisteet :tyyppi tyyppi}
                       (when pisteet-id {:id pisteet-id}))
          url (cond
                pisteet :tallenna-kuukausittaiset-pisteet
                (and (nil? pisteet) pisteet-id) :poista-kuukausittaiset-pisteet
                :else nil)
          ;; Lopetetaan kaikkien avaus
          kuukausipisteet (map #(merge % {:avattu-muokattavaksi? false}) (:kuukausipisteet app))]
      (do
        ;; Blurrin takia tätä kutsutaan myös tyhjäksi jätetyillä kuukausilla. Ei tehdä mitään näissä tapauksissa.
        (when-not (nil? url)
          (tuck-apurit/post! url
                             parametrit
                             {:onnistui ->TallennaKuukausipisteetOnnistui
                              :epaonnistui ->TallennaKuukausipisteetEpaonnistui}))
        (-> app
            (assoc :kuukausipisteet kuukausipisteet)
            (dissoc :kuukausipisteet-ehdotus)))))

  AvaaKuukausipisteetMuokattavaksi
  (process-event [{kuukausi :kuukausi} app]
    (let [valitun-kuukauden-pisteet (:pisteet (first (keep #(when (= (:kuukausi %) kuukausi) %) (:kuukausipisteet app))))
          kuukausipisteet (map
                            (fn [p]
                              ;; Muokataan listan elementti, jos kuukausi täsmää
                              (if (not= kuukausi (:kuukausi p))
                                (merge p {:avattu-muokattavaksi? false})
                                (merge p {:avattu-muokattavaksi? true})))
                            (:kuukausipisteet app))]
      (-> app
          (assoc-in [:kuukausipisteet-ehdotus (keyword (str kuukausi))] valitun-kuukauden-pisteet)
          (assoc :kuukausipisteet kuukausipisteet))))

  TallennaKuukausipisteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (hae-urakan-lupaustiedot app (-> @tila/tila :yleiset :urakka))
    (viesti/nayta-toast! "Kuukausipisteet annettiin onnistuneesti" :onnistui)
    app)

  TallennaKuukausipisteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Kuukausipisteiden lisäys epäonnistui!" :varoitus)
    app)

  AsetaNykyhetki
  (process-event [{nykyhetki :nykyhetki} app]
    (-> app
        (assoc :nykyhetki nykyhetki)
        hae-urakan-lupaustiedot)))
