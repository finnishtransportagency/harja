(ns harja.tiedot.urakka.suunnittelu.suolarajoitukset-tiedot
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.viesti :as viesti]
            [clojure.set :as set]))

;;Validoinnit
(def lomake-atom (atom {}))
(defn validoinnit [avain]
  (avain
    {:tie [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
     :aosa [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
     :losa [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
     :aet [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
     :let [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)
           (tila/silloin-kun #(not (nil? (:aet @lomake-atom)))
             (fn [arvo]
               ;; Validointi vaatii "nil" vastauksen, kun homma on pielessä ja kentän arvon, kun kaikki on ok
               (cond
                 ;; Jos alkuosa ja loppuosa on sama
                 ;; Ja alkuetäisyys on pienempi kuin loppuetäisyys)
                 (and (= (:aosa @lomake-atom) (:losa @lomake-atom)) (< (:aet @lomake-atom) arvo))
                 arvo
                 ;; Alkuetäisyys on suurempi kuin loppuetäisyys
                 (and (= (:aosa @lomake-atom) (:losa @lomake-atom)) (>= (:aet @lomake-atom) arvo))
                 nil
                 :else arvo)))]
     :suolarajoitus [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 1000 %)]}))
(defn- validoi-lomake [lomake]
  (apply tila/luo-validius-tarkistukset
    [[:tie] (validoinnit :tie)
     [:aosa] (validoinnit :aosa)
     [:losa] (validoinnit :losa)
     [:aet] (validoinnit :aet)
     [:let] (validoinnit :let)
     [:suolarajoitus] (validoinnit :suolarajoitus)]))


;; Filtterit
(defrecord ValitseHoitovuosi [vuosi])

(defrecord HaeSuolarajoitukset [valittu-vuosi])
(defrecord HaeSuolarajoituksetOnnistui [vastaus])
(defrecord HaeSuolarajoituksetEpaonnistui [vastaus])

(defrecord HaeTalvisuolanKayttorajat [valittu-vuosi])
(defrecord HaeTalvisuolanKayttorajatOnnistui [vastaus])
(defrecord HaeTalvisuolanKayttorajatEpaonnistui [vastaus])

;; Käyttörajalomake
(defrecord PaivitaKayttorajalomake [lomake])
(defrecord TallennaKayttorajalomake [])
(defrecord TallennaKayttorajalomakeOnnistui [vastaus])
(defrecord TallennaKayttorajalomakeEpaonnistui [vastaus])

;; RajoitusalueidenSanktiolomake
(defrecord PaivitaRajoitusalueidenSanktiolomake [lomake])
(defrecord TallennaRajoitusalueidenSanktiolomake [])
(defrecord TallennaRajoitusalueidenSanktiolomakeOnnistui [vastaus])
(defrecord TallennaRajoitusalueidenSanktiolomakeEpaonnistui [vastaus])

(defrecord AvaaTaiSuljeSivupaneeli [tila lomakedata])

;; Suolarajoituslomakkeen Päivitys
(defrecord PaivitaLomake [lomake tarkista-tierekisteri?])
(defrecord HaeTierekisterinTiedotOnnistui [vastaus])
(defrecord HaeTierekisterinTiedotEpaonnistui [vastaus])

(defrecord TallennaLomake [lomake tila])
(defrecord TallennaLomakeOnnistui [vastaus])
(defrecord TallennaLomakeEpaonnistui [vastaus])
;; Poista
(defrecord PoistaSuolarajoitus [parametrit])
(defrecord PoistaSuolarajoitusOnnistui [vastaus])
(defrecord PoistaSuolarajoitusEpaonnistui [vastaus])

(defn- hae-suolarajoitukset [valittu-vuosi]
  (let [urakka-id (-> @tila/yleiset :urakka :id)
        _ (tuck-apurit/post! :hae-suolarajoitukset
            {:hoitokauden_alkuvuosi valittu-vuosi
             :urakka_id urakka-id}
            {:onnistui ->HaeSuolarajoituksetOnnistui
             :epaonnistui ->HaeSuolarajoituksetEpaonnistui
             :paasta-virhe-lapi? true})]))

(defn- hae-kayttorajat [valittu-vuosi]
  (let [urakka-id (-> @tila/yleiset :urakka :id)
        _ (tuck-apurit/post! :hae-talvisuolan-kayttorajat
            {:hoitokauden-alkuvuosi valittu-vuosi
             :urakka-id urakka-id}
            {:onnistui ->HaeTalvisuolanKayttorajatOnnistui
             :epaonnistui ->HaeTalvisuolanKayttorajatEpaonnistui
             :paasta-virhe-lapi? true})]))

(extend-protocol tuck/Event

  ValitseHoitovuosi
  (process-event [{vuosi :vuosi} app]
    (do
      (urakka/valitse-aikavali! (pvm/->pvm (str "1.10." vuosi)) (pvm/->pvm (str "30.9." (inc vuosi))))
      (hae-suolarajoitukset vuosi)
      (hae-kayttorajat vuosi)
      (-> app
        (assoc :suolarajoitukset nil)
        (assoc :kayttorajat nil)
        (assoc :valittu-hoitovuosi vuosi))))

  HaeSuolarajoitukset
  (process-event [{valittu-vuosi :valittu-vuosi} app]
    (do
      (hae-suolarajoitukset valittu-vuosi)
      (-> app
        (assoc :suolarajoitukset nil)
        (assoc :suolarajoitukset-haku-kaynnissa? true))))

  HaeSuolarajoituksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [; Lisätään ui taulukkoa varten osoiteväli
          vastaus (map (fn [rivi]
                         (assoc rivi :osoitevali (str
                                                   (str (:aosa rivi) " / " (:aet rivi))
                                                   " – "
                                                   (str (:losa rivi) " / " (:let rivi)))))
                    vastaus)]
      (-> app
        (assoc :suolarajoitukset-haku-kaynnissa? false)
        (assoc :suolarajoitukset vastaus))))

  HaeSuolarajoituksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Suolarajoitusten haku epäonnistui tallennus onnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (-> app
        (assoc :suolarajoitukset-haku-kaynnissa? false)
        (assoc :suolarajoitukset nil))))

  HaeTalvisuolanKayttorajat
  (process-event [{valittu-vuosi :valittu-vuosi} app]
    (let [_ (hae-kayttorajat valittu-vuosi)]

      (-> app
        (assoc :kayttorajat nil)
        (assoc :kayttoraja-haku-kaynnissa? true))))

  HaeTalvisuolanKayttorajatOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :kayttoraja-haku-kaynnissa? false)
      (assoc :kayttorajat vastaus)))

  HaeTalvisuolanKayttorajatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Talvisuolan kokonaismäärän käyttörajan haku epäonnistui tallennus onnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (-> app
        (assoc :kayttoraja-haku-kaynnissa? false)
        (assoc :kayttorajat nil))))

  PaivitaKayttorajalomake
  (process-event [{lomake :lomake} app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          lomake {:id (:id lomake)
                  :sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta lomake)
                  :indeksi (:indeksi lomake)
                  :hoitokauden-alkuvuosi (:valittu-hoitovuosi app)
                  :urakka-id urakka-id
                  :kopioi-rajoitukset (:kopioi-rajoitukset lomake)}]

      (-> app
        (assoc-in [:kayttorajat :talvisuolan-sanktiot] lomake)
        (assoc :paivita-kayttoraja-kaynnissa? true))))

  TallennaKayttorajalomake
  (process-event [_ app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          lomake (get-in app [:kayttorajat :talvisuolan-sanktiot])
          lomake-data {:id (:id lomake)
                       :sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta lomake)
                       :indeksi (:indeksi lomake)
                       :hoitokauden-alkuvuosi (:valittu-hoitovuosi app)
                       :urakka-id urakka-id
                       :kopioidaan-tuleville-vuosille? (:kopioi-rajoitukset lomake)}

          ;;TODO: Jos muutetaan vain checkboxistsa kopiointia seuraaville vuosille. Ei tuck postia saa tehdä


          _ (tuck-apurit/post! :tallenna-talvisuolan-kayttoraja
              lomake-data
              {:onnistui ->TallennaKayttorajalomakeOnnistui
               :epaonnistui ->TallennaKayttorajalomakeEpaonnistui
               :paasta-virhe-lapi? true})]
      (assoc app :paivita-kayttoraja-kaynnissa? true)))

  TallennaKayttorajalomakeOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
        (viesti/nayta-toast! "Tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
        (-> app
          (assoc-in [:kayttorajat :talvisuolan-sanktiot] vastaus)
          (assoc :paivita-kayttoraja-kaynnissa? false))))

  TallennaKayttorajalomakeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-lyhyt)
      (assoc app :paivita-kayttoraja-kaynnissa? false)))

  PaivitaRajoitusalueidenSanktiolomake
  (process-event [{lomake :lomake} app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          lomake {:id (:id lomake)
                  :sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta lomake)
                  :indeksi (:indeksi lomake)
                  :hoitokauden-alkuvuosi (:valittu-hoitovuosi app)
                  :urakka-id urakka-id
                  :kopioi-rajoitukset (:kopioi-rajoitukset lomake)}]
      (assoc-in app [:kayttorajat :rajoitusalueiden-suolasanktio] lomake)))

  TallennaRajoitusalueidenSanktiolomake
  (process-event [_ app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          lomake (get-in app [:kayttorajat :rajoitusalueiden-suolasanktio])
          lomake-data {:id (:id lomake)
                       :sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta lomake)
                       :indeksi (:indeksi lomake)
                       :hoitokauden-alkuvuosi (:valittu-hoitovuosi app)
                       :urakka-id urakka-id
                       :kopioidaan-tuleville-vuosille? (:kopioi-rajoitukset lomake)}

          ;;TODO: Jos muutetaan vain checkboxistsa kopiointia seuraaville vuosille. Ei tuck postia saa tehdä


          _ (tuck-apurit/post! :tallenna-rajoitusalueen-sanktio
              lomake-data
              {:onnistui ->TallennaRajoitusalueidenSanktiolomakeOnnistui
               :epaonnistui ->TallennaRajoitusalueidenSanktiolomakeEpaonnistui
               :paasta-virhe-lapi? true})]
      (assoc app :paivita-rajoitusalue-sanktio-kaynnissa? true)))

  TallennaRajoitusalueidenSanktiolomakeOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
      (-> app
        (assoc-in [:kayttorajat :rajoitusalueiden-suolasanktio] vastaus)
        (assoc :paivita-rajoitusalue-sanktio-kaynnissa? false))))

  TallennaRajoitusalueidenSanktiolomakeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-lyhyt)
      (assoc app :paivita-rajoitusalue-sanktio-kaynnissa? false)))

  AvaaTaiSuljeSivupaneeli
  (process-event [{tila :tila lomakedata :lomakedata} app]
    (let [_ (reset! lomake-atom lomakedata)
          {:keys [validoi] :as validoinnit} (validoi-lomake lomakedata)
          {:keys [validi? validius]} (validoi validoinnit lomakedata)]
      (-> app
        (assoc :rajoitusalue-lomake-auki? tila)
        (assoc :lomake lomakedata)
        (assoc-in [:lomake ::tila/validius] validius)
        (assoc-in [:lomake ::tila/validi?] validi?))))

  ;; Päivitetään lomakkeen sisältö app-stateen, mutta ei serverille
  PaivitaLomake
  (process-event [{lomake :lomake tarkista-tierekisteri? :tarkista-tierekisteri?} app]
    (let [_ (js/console.log "PaivitaLomake")
          urakka-id (-> @tila/yleiset :urakka :id)
          vanha-tierekisteri (into #{} (select-keys (:lomake app) [:tie :aosa :aet :losa :let]))
          uusi-tierekisteri (into #{} (select-keys lomake [:tie :aosa :aet :losa :let]))
          _ (reset! lomake-atom lomake)
          {:keys [validoi] :as validoinnit} (validoi-lomake lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)
          ;; Tarkista palvelinvirheet
          palvelinvirheet (get-in lomake [:palvelinvirheet :tierekisteri])
          validius (if-not (nil? palvelinvirheet)
                     (mapv (fn [virhe] (assoc validius (key virhe) (val virhe))) palvelinvirheet)
                     validius)
          validi? (if-not (nil? palvelinvirheet)
                    false
                    validi?)
          app (-> app
                (assoc :lomake lomake)
                (assoc-in [:lomake ::tila/validius] validius)
                (assoc-in [:lomake ::tila/validi?] validi?))
          app (if (or
                    tarkista-tierekisteri?
                    (and
                        (not (nil? (:tie lomake)))
                        (not (nil? (:aosa lomake)))
                        (not (nil? (:aet lomake)))
                        (not (nil? (:losa lomake)))
                        (not (nil? (:let lomake)))
                      (or
                        (not (empty? (set/difference vanha-tierekisteri uusi-tierekisteri)))
                        (not= (count vanha-tierekisteri) (count uusi-tierekisteri)))))
                (do
                  (tuck-apurit/post! :tierekisterin-tiedot
                    {:rajoitusalue-id (:rajoitusalue_id lomake)
                     :tie (:tie lomake)
                     :aosa (:aosa lomake)
                     :aet (:aet lomake)
                     :losa (:losa lomake)
                     :let (:let lomake)
                     :urakka-id urakka-id
                     :hoitokauden-alkuvuosi (:valittu-hoitovuosi app)}

                    {:onnistui ->HaeTierekisterinTiedotOnnistui
                     :epaonnistui ->HaeTierekisterinTiedotEpaonnistui
                     :paasta-virhe-lapi? true})
                  (assoc app :hae-tiedot-kaynnissa? true))
                app)]
      app))

  HaeTierekisterinTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaeTierekisterinTiedotOnnistui :: vastaus" (pr-str vastaus))
      (-> app
        (assoc-in [:lomake :palvelinvirheet :tierekisteri] nil)
        (assoc-in [:lomake :pituus] (:pituus vastaus))
        (assoc-in [:lomake :ajoratojen_pituus] (:ajoratojen_pituus vastaus))
        (assoc-in [:lomake :pohjavesialueet] (:pohjavesialueet vastaus))
        (assoc :hae-tiedot-kaynnissa? false))))

  HaeTierekisterinTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [tierekisterivirhe (when (get-in vastaus [:response :virhe])
                              (get-in vastaus [:response :virhe]))
          tievalidointi (merge
                          (get-in app [:lomake :harja.tiedot.urakka.urakka/validius [:tie]])
                          {:validi? false :virheteksti tierekisterivirhe})]
      (do

           (viesti/nayta-toast! "Tierekisteriosoitteen käsittelyssä virhe." :varoitus viesti/viestin-nayttoaika-pitka)
           (cond-> app
             ;; Virheen käsittely lomakkeella on kaksiportainen, kun osa validoinnista tapahtuu clientin päässä ja osa palvelimella.
             ;; Palvelimelta saadut virheilmoitukset poistuu lomakkeelta heti, kun se validoidaan uudestaan.
             ;; Asetetaan siis palvelimelta saadut virheilmoitukset talteen, jos niitä tulee
             (not tierekisterivirhe) (assoc-in [:lomake :palvelinvirheet :tierekisteri] nil)
             tierekisterivirhe (assoc-in [:lomake :palvelinvirheet :tierekisteri :tie] tierekisterivirhe)
             tierekisterivirhe (assoc-in [:lomake :harja.tiedot.urakka.urakka/validius [:tie]] tievalidointi)
             true (assoc :hae-tiedot-kaynnissa? false)))))

  TallennaLomake
  (process-event [{lomake :lomake sivupaneeli-tila :tila} app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          tallennettava-lomake {:hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi lomake)
                                :urakka_id urakka-id
                                :suolarajoitus (:suolarajoitus lomake)
                                :formiaatti (:formiaatti lomake)
                                :rajoitusalue_id (:rajoitusalue_id lomake)
                                :rajoitus_id (:rajoitus_id lomake)
                                :tie (:tie lomake)
                                :aosa (:aosa lomake)
                                :aet (:aet lomake)
                                :losa (:losa lomake)
                                :let (:let lomake)
                                :pituus (:pituus lomake)
                                :ajoratojen_pituus (:ajoratojen_pituus lomake)
                                :kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? lomake)}
          _ (tuck-apurit/post! :tallenna-suolarajoitus tallennettava-lomake
              {:onnistui ->TallennaLomakeOnnistui
               :epaonnistui ->TallennaLomakeEpaonnistui
               :paasta-virhe-lapi? true})]

      (-> app
        (assoc :tallennus-kaynnissa? true)
        (assoc :rajoitusalue-lomake-auki? sivupaneeli-tila))))

  TallennaLomakeOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [e! (tuck/current-send-function)
          tallennettu-lomake (:lomake app)
          tallennettu-lomake (dissoc tallennettu-lomake :rajoitusalue_id :rajoitus_id)
          e! (tuck/current-send-function)
          _ (when (app :rajoitusalue-lomake-auki?)
              (.setTimeout js/window
                (tuck/send-async! ->PaivitaLomake tallennettu-lomake true)
                100))]
      (do
        (viesti/nayta-toast! "Rajoitusalueen tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
        (hae-suolarajoitukset (:valittu-hoitovuosi app))

        (-> (if (app :rajoitusalue-lomake-auki?)
              (assoc app :lomake tallennettu-lomake)
              (assoc app :lomake nil))
          (assoc :suolarajoitukset-haku-kaynnissa? true)
          (assoc :tallennus-kaynnissa? false)))))

  TallennaLomakeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Rajoitusalueen tallennus epäonnistui!" :varoitus viesti/viestin-nayttoaika-pitka)
    (assoc app :tallennus-kaynnissa? false))

  PoistaSuolarajoitus
  (process-event [{parametrit :parametrit} app]
    (let [hoitokauden-alkuvuosi (pvm/vuosi (first @urakka/valittu-hoitokausi))
          urakka-id (-> @tila/yleiset :urakka :id)
          _ (tuck-apurit/post! :poista-suolarajoitus
              {:hoitokauden_alkuvuosi hoitokauden-alkuvuosi
               :urakka_id urakka-id
               :rajoitusalue_id (:rajoitusalue_id parametrit)
               :kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? parametrit)}
              {:onnistui ->PoistaSuolarajoitusOnnistui
               :epaonnistui ->PoistaSuolarajoitusEpaonnistui
               :paasta-virhe-lapi? true})]
      (-> app
        (assoc :poisto-kaynnissa? true)
        (assoc :rajoitusalue-lomake-auki? false))))

  PoistaSuolarajoitusOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Rajoitusalueen poistaminen onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
      (hae-suolarajoitukset (:valittu-hoitovuosi app))
      (-> app
        (assoc :suolarajoitukset-haku-kaynnissa? true)
        (assoc :poisto-kaynnissa? false)
        (assoc :lomake nil))))

  PoistaSuolarajoitusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Rajoitusalueen tallennus epäonnistui!" :varoitus viesti/viestin-nayttoaika-pitka)
      (assoc app :poisto-kaynnissa? false))))
