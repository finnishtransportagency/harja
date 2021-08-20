(ns harja.tiedot.urakka.lupaukset
  "Urakan lupausten tiedot."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! >! chan]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defrecord HaeUrakanLupaustiedot [urakka])
(defrecord HaeUrakanLupaustiedotOnnnistui [vastaus])
(defrecord HaeUrakanLupaustiedotEpaonnistui [vastaus])
(defrecord HaeLupauksenVastausvaihtoehdot [vastaus])
(defrecord HaeLupauksenVastausvaihtoehdotOnnistui [vastaus])
(defrecord HaeLupauksenVastausvaihtoehdotEpaonnistui [vastaus])

(defrecord HaeKommentitOnnistui [vastaus])
(defrecord HaeKommentitEpaonnistui [vastaus])
(defrecord LisaaKommentti [kommentti])
(defrecord LisaaKommenttiOnnistui [vastaus])
(defrecord LisaaKommenttiEpaonnistui [vastaus])
(defrecord PoistaKommentti [id])
(defrecord PoistaKommenttiOnnistui [vastaus])
(defrecord PoistaKommenttiEpaonnistui [vastaus])

(defrecord HoitokausiVaihdettu [urakka hoitokausi])

(defrecord VaihdaLuvattujenPisteidenMuokkausTila [])
(defrecord LuvattujaPisteitaMuokattu [pisteet])
(defrecord TallennaLupausSitoutuminen [urakka])
(defrecord TallennaLupausSitoutuminenOnnnistui [vastaus])
(defrecord TallennaLupausSitoutuminenEpaonnistui [vastaus])

(defrecord AvaaLupausvastaus [vastaus])
(defrecord SuljeLupausvastaus [vastaus])
(defrecord ValitseVastausKuukausi [kuukausi])
(defrecord AvaaLupausryhma [kirjain])

(defrecord AlustaNakyma [urakka])
(defrecord NakymastaPoistuttiin [])

(defrecord ValitseVaihtoehto [vaihtoehto lupaus kohdekuukausi kohdevuosi])
(defrecord ValitseVaihtoehtoOnnistui [vastaus])
(defrecord ValitseVaihtoehtoEpaonnistui [vastaus])

(defrecord ValitseKE [vastaus lupaus kohdekuukausi kohdevuosi])
(defrecord ValitseKEOnnistui [vastaus])
(defrecord ValitseKEEpaonnistui [vastaus])

(defn- lupausten-hakuparametrit [urakka hoitokausi]
  {:urakka-id (:id urakka)
   :urakan-alkuvuosi (pvm/vuosi (:alkupvm urakka))
   :valittu-hoitokausi hoitokausi})

(defn hae-urakan-lupausitiedot [app urakka]
  (tuck-apurit/post! :hae-urakan-lupaustiedot
                     (lupausten-hakuparametrit urakka (:valittu-hoitokausi app))
                     {:onnistui ->HaeUrakanLupaustiedotOnnnistui
                      :epaonnistui ->HaeUrakanLupaustiedotEpaonnistui}))

(defn hae-kommentit
  "Päivitä kommenttien listaus (ei tyhjennetä listaa, eikä näytetä 'Ladataan kommentteja' -viestiä."
  [app]
  (tuck-apurit/post!
    app
    :lupauksen-kommentit
    {:lupaus-id (get-in app [:vastaus-lomake :lupaus-id])
     :urakka-id (-> @tila/tila :yleiset :urakka :id)
     :kuukausi (get-in app [:vastaus-lomake :vastauskuukausi])
     :vuosi (get-in app [:vastaus-lomake :vastausvuosi])}
    {:onnistui ->HaeKommentitOnnistui
     :epaonnistui ->HaeKommentitEpaonnistui}))

(defn tyhjenna-ja-hae-kommentit
  "Tyhjennä kommenttien listaus, näytä 'Ladataan kommentteja' -viesti, ja hae kommentit uudelleen."
  [app]
  (-> app
      (assoc-in [:lupaukset :vastaus] nil)
      (assoc-in [:lupaukset :haku-kaynnissa?] true)
      (hae-kommentit)))

(extend-protocol tuck/Event

  HoitokausiVaihdettu
  (process-event [{urakka :urakka hoitokausi :hoitokausi} app]
    (hae-urakan-lupausitiedot app urakka)
    (-> app
        (assoc :valittu-hoitokausi hoitokausi)))

  HaeUrakanLupaustiedot
  (process-event [{urakka :urakka} app]
    (hae-urakan-lupausitiedot app urakka)
    app)

  HaeUrakanLupaustiedotOnnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Jos lomake on auki, niin haetaan lomakkeelle uudistuneet tiedot
    (let [lomakkeen-tiedot (:vastaus-lomake app)
          lupaukset (:lupaukset vastaus)
          flatten-lupaukset (flatten (reduce (fn [kaikki avain]
                                               (conj kaikki (get-in lupaukset [avain])))
                                             []
                                             (keys lupaukset)))
          uudistunut-lomake (if lomakkeen-tiedot
                              (first (filter (fn [item]
                                               (when (and
                                                       (= (:lupaus-id lomakkeen-tiedot) (:lupaus-id item))
                                                       (= (:lupausryhma-id lomakkeen-tiedot) (:lupausryhma-id item)))
                                                 item))
                                             flatten-lupaukset))
                              nil)
          app (merge app vastaus)
          app (if uudistunut-lomake
                (update app :vastaus-lomake merge uudistunut-lomake)
                app)]
      app))

  HaeUrakanLupaustiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Lupaustietojen hakeminen epäonnistui!" :varoitus)
    app)

  HaeLupauksenVastausvaihtoehdot
  (process-event [{vastaus :vastaus} app]
    (do
      (tuck-apurit/post! :lupauksen-vastausvaihtoehdot
                         {:lupaus-id (:lupaus-id vastaus)}
                         {:onnistui ->HaeLupauksenVastausvaihtoehdotOnnistui
                          :epaonnistui ->HaeLupauksenVastausvaihtoehdotEpaonnistui})
      app))

  HaeLupauksenVastausvaihtoehdotOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc :lomake-lupauksen-vaihtoehdot vastaus)
        (tyhjenna-ja-hae-kommentit)))

  HaeLupauksenVastausvaihtoehdotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Vastausvaihtoehtojen hakeminen epäonnistui!" :varoitus)
    (dissoc app :lomake-lupauksen-vaihtoehdot))

  HaeKommentitOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc-in [:kommentit :haku-kaynnissa?] false)
        (assoc-in [:kommentit :lisays-kaynnissa?] false)
        (assoc-in [:kommentit :poisto-kaynnissa?] false)
        (assoc-in [:kommentit :vastaus] vastaus)))

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
    (let [parametrit (merge (lupausten-hakuparametrit urakka (:valittu-hoitokausi app))
                            {:id (get-in app [:lupaus-sitoutuminen :id])
                             :pisteet (get-in app [:lupaus-sitoutuminen :pisteet])})]
      (-> app
          (tuck-apurit/post! :tallenna-luvatut-pisteet
                             parametrit
                             {:onnistui ->TallennaLupausSitoutuminenOnnnistui
                              :epaonnistui ->TallennaLupausSitoutuminenEpaonnistui}))))

  TallennaLupausSitoutuminenOnnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (merge vastaus)
        (assoc :muokkaa-luvattuja-pisteita? false)))

  TallennaLupausSitoutuminenEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast!
      "TallennaLupausSitoutuminenOnnnistui tallennus epäonnistui"
      :varoitus
      viesti/viestin-nayttoaika-aareton)
    app)

  AvaaLupausvastaus
  (process-event [{vastaus :vastaus} app]
    ;; Avataansivupaneeli, lisätään vastauksen tiedot :vastaus-lomake avaimeen

    (let [kuukausi (pvm/kuukausi (pvm/nyt))
          vuosi (pvm/vuosi (pvm/nyt))]
      (tuck-apurit/post! :lupauksen-vastausvaihtoehdot
                         {:lupaus-id (:lupaus-id vastaus)}
                         {:onnistui ->HaeLupauksenVastausvaihtoehdotOnnistui
                          :epaonnistui ->HaeLupauksenVastausvaihtoehdotEpaonnistui})
      (-> app
          (assoc :vastaus-lomake vastaus)
          ;; Alustava vastauskuukausi
          (assoc-in [:vastaus-lomake :vastauskuukausi] kuukausi)
          (assoc-in [:vastaus-lomake :vastausvuosi] vuosi)
          (assoc-in [:kommentit :haku-kaynnissa?] true)
          (assoc-in [:kommentit :vastaus] nil))))

  SuljeLupausvastaus
  (process-event [_ app]
    ;; Suljetaan sivupaneeli
    (dissoc app :vastaus-lomake))

  ValitseVastausKuukausi
  (process-event [{kuukausi :kuukausi} app]
    (let [vastausvuosi (if (>= kuukausi 10)
                         (pvm/vuosi (first (:valittu-hoitokausi app)))
                         (pvm/vuosi (second (:valittu-hoitokausi app))))]
      (-> app
          (assoc-in [:vastaus-lomake :vastauskuukausi] kuukausi)
          (assoc-in [:vastaus-lomake :vastausvuosi] vastausvuosi)
          (tyhjenna-ja-hae-kommentit))))

  AvaaLupausryhma
  (process-event [{kirjain :kirjain} app]
    (let [avoimet-lupausryhmat (if (:avoimet-lupausryhmat app)
                                 (into #{} (:avoimet-lupausryhmat app))
                                 #{})
          avoimet-lupausryhmat (if (contains? avoimet-lupausryhmat kirjain)
                                 (into #{} (disj avoimet-lupausryhmat kirjain))
                                 (into #{} (cons kirjain avoimet-lupausryhmat)))]
      (assoc app :avoimet-lupausryhmat avoimet-lupausryhmat)))

  AlustaNakyma
  (process-event [{urakka :urakka} app]
    (let [hoitokaudet (u/hoito-tai-sopimuskaudet urakka)]
      (assoc app :urakan-hoitokaudet hoitokaudet
                 :valittu-hoitokausi (u/paattele-valittu-hoitokausi hoitokaudet))))

  NakymastaPoistuttiin
  (process-event [_ app]
    app)

  ValitseVaihtoehto
  (process-event [{vaihtoehto :vaihtoehto lupaus :lupaus kohdekuukausi :kohdekuukausi kohdevuosi :kohdevuosi} app]
    (do
      (tuck-apurit/post! :vastaa-lupaukseen
                         (merge
                           ;; Lisätään vastauksen id palvelupyyntöön vastauksen editoimiseksi, jos sellainen on tarjolla
                           (when (:kuukauden-vastaus-id vaihtoehto)
                             {:id (:kuukauden-vastaus-id vaihtoehto)})
                           {:lupaus-id (:lupaus-id lupaus)
                            :urakka-id (-> @tila/tila :yleiset :urakka :id)
                            :kuukausi kohdekuukausi
                            :vuosi kohdevuosi
                            :paatos (if (= kohdekuukausi (:paatos-kk lupaus))
                                      true false)
                            :vastaus nil
                            :lupaus-vaihtoehto-id (:id vaihtoehto)})
                         {:onnistui ->ValitseVaihtoehtoOnnistui
                          :epaonnistui ->ValitseVaihtoehtoEpaonnistui})
      (assoc app :vastaus vaihtoehto)))

  ValitseVaihtoehtoOnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Koska vastauksen antaminen muuttaa sekä vastauslomaketta, että vastauslistaa, niin haetaan koko setti uusiksi
    (hae-urakan-lupausitiedot app (-> @tila/tila :yleiset :urakka))
    app)

  ValitseVaihtoehtoEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Vastauksen antaminen epäonnistui!" :varoitus)
    app)

  ValitseKE
  (process-event [{vastaus :vastaus lupaus :lupaus kohdekuukausi :kohdekuukausi kohdevuosi :kohdevuosi} app]
    (do
      (tuck-apurit/post! :vastaa-lupaukseen
                         (merge (when (:kuukauden-vastaus-id vastaus)
                                  {:id (:kuukauden-vastaus-id vastaus)})
                                {:lupaus-id (:lupaus-id lupaus)
                                 :urakka-id (-> @tila/tila :yleiset :urakka :id)
                                 :kuukausi kohdekuukausi
                                 :vuosi kohdevuosi
                                 :paatos (if (= kohdekuukausi (:paatos-kk lupaus))
                                           true false)
                                 :vastaus (:vastaus vastaus)
                                 :lupaus-vaihtoehto-id nil})
                         {:onnistui ->ValitseKEOnnistui
                          :epaonnistui ->ValitseKEEpaonnistui})
      (assoc-in app [:vastaus-lomake :vastaus-ke] (:vastaus vastaus))))

  ValitseKEOnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Koska vastauksen antaminen muuttaa sekä vastauslomaketta, että vastauslistaa, niin haetaan koko setti uusiksi
    (hae-urakan-lupausitiedot app (-> @tila/tila :yleiset :urakka))
    app)

  ValitseKEEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Vastauksen antaminen epäonnistui!" :varoitus)
    app)

  )