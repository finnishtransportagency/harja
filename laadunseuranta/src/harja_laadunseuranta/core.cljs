(ns harja-laadunseuranta.core
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.paikannus :as paikannus]
            [harja-laadunseuranta.main :as main]
            [harja-laadunseuranta.tiedot.sovellus :as sovellus]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.tr-haku :as tr-haku]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.puhe :as puhe]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tarkastusajon-luonti]
            [cljs.core.async :as async :refer [<!]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [clojure.string :as str]
            [harja-laadunseuranta.ui.yleiset.dom :as dom]
            [harja-laadunseuranta.asiakas.tapahtumat :as tapahtumat]
            [cljs-time.local :as l])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(enable-console-print!)

(defn render []
  (reagent/render-component [main/main] (.getElementById js/document "app")))

(defn- esta-mobiililaitteen-nayton-lukitus []
  ;; PENDING Dirty hack, joka toimii vain Android-laitteissa
  ;; Kannattaa pitää silmällä Wake Lock APIa: http://boiler23.github.io/screen-wake/
  (let [video-paalla (atom false)]
    (let [video (.getElementById js/document "keep-alive-hack")
          soita-video (fn [elementti]
                        (when (and (not @video-paalla) elementti)
                          (.log js/console "Estetään näytön lukko")
                          (.play elementti)
                          (reset! video-paalla true)))]
      ;; Soiton täytyy alkaa suoraan user eventistä
      (.addEventListener js/document.body "click" #(soita-video video)))))

(defonce paikannus-id (cljs.core/atom nil))

(defn- alusta-paikannus-id []
  (reset! paikannus-id (paikannus/kaynnista-paikannus
                         {:sijainti-atom sovellus/sijainti
                          :ensimmainen-sijainti-yritys-atom sovellus/ensimmainen-sijainti-yritys
                          :ensimmainen-sijainti-saatu-atom sovellus/ensimmainen-sijainti-saatu
                          :ensimmainen-sijainti-virhekoodi-atom sovellus/ensimmainen-sijainti-virhekoodi})))

(defn- alusta-geolokaatio-api []
  (if (paikannus/geolokaatio-tuettu?)
    (reset! sovellus/gps-tuettu true)))

(defn- kuuntele-dom-eventteja []
  (dom/kuuntele-leveyksia)
  (dom/kuuntele-body-klikkauksia))

(defn- kasittele-sivun-nakyvyysmuutos [tarkastusajo-kaynnissa-atom
                                       kuvaa-otetaan-atom]
  (let [piilossa? js/document.hidden]
    (when (and (not piilossa?) ;; Tultiin piilosta pois
               (not @kuvaa-otetaan-atom)
               @tarkastusajo-kaynnissa-atom)
      (ilmoitukset/ilmoita
        "Pidä sovellus näkyvillä, muuten merkinnät eivät tallennu!"
        sovellus/ilmoitus
        {:tyyppi :varoitus}))))

(defn- kuuntele-sivun-nakyvyytta [tarkastusajo-kaynnissa-atom kuvaa-otetaan-atom]
  (.addEventListener js/document "visibilitychange"
                     #(kasittele-sivun-nakyvyysmuutos
                        tarkastusajo-kaynnissa-atom
                        kuvaa-otetaan-atom)))

(defn kaynnista-kayttajatietojen-haku []
  ;; Haetaan käyttäjätiedot kun laite on paikannettu
  ;; Sijainti tarvitaan urakoiden lajitteluun, jotta defaulttina on valittuna lähin
  (run!
    (when (and @sovellus/ensimmainen-sijainti-saatu
               (not @sovellus/kayttajanimi))
      (go (let [kayttajatiedot (<! (comms/hae-kayttajatiedot (:nykyinen @sovellus/sijainti)))]
            (reset! sovellus/kayttajanimi (-> kayttajatiedot :ok :nimi))
            (reset! sovellus/kayttajatunnus (-> kayttajatiedot :ok :kayttajanimi))
            (if (-> kayttajatiedot :ok :kayttajanimi)
              (reset! sovellus/kayttaja-tunnistettu true)
              (reset! sovellus/kayttaja-tunnistettu false))
            (reset! sovellus/oikeus-urakoihin (-> kayttajatiedot :ok :urakat))
            (if (not (empty? (-> kayttajatiedot :ok :urakat)))
              (reset! sovellus/kayttajalla-oikeus-ainakin-yhteen-urakkaan true)
              (reset! sovellus/kayttajalla-oikeus-ainakin-yhteen-urakkaan false))
            (reset! sovellus/roolit (-> kayttajatiedot :ok :roolit))
            (reset! sovellus/organisaatio (-> kayttajatiedot :ok :organisaatio)))))))

(defn- alusta-sovellus []
  (go

    (reset! sovellus/idxdb (<! (reitintallennus/tietokannan-alustus sovellus/idxdb-tuettu)))

    (reitintallennus/palauta-tarkastusajo @sovellus/idxdb #(do
                                                             (reset! sovellus/palautettava-tarkastusajo %)
                                                             (when (= "?relogin=true" js/window.location.search)
                                                               (tarkastusajon-luonti/jatka-ajoa!))))

    (reitintallennus/paivita-lahettamattomien-merkintojen-maara!
      @sovellus/idxdb
      asetukset/+pollausvali+
      sovellus/lahettamattomia-merkintoja)

    (kaynnista-kayttajatietojen-haku)

    (reitintallennus/kaynnista-reitinlahetys asetukset/+pollausvali+
                                             @sovellus/idxdb
                                             comms/laheta-reittimerkinnat!)
    (reitintallennus/kaynnista-reitintallennus
      {:sijainnin-tallennus-mahdollinen-atom sovellus/sijainnin-tallennus-mahdollinen
       :sijainti-atom sovellus/sijainti
       :db @sovellus/idxdb
       :tarkastusajo-paattymassa-atom sovellus/tarkastusajo-paattymassa?
       :segmentti-atom sovellus/reittisegmentti
       :reittipisteet-atom sovellus/reittipisteet
       :tarkastusajo-kaynnissa-atom sovellus/tarkastusajo-kaynnissa?
       :tarkastusajo-atom sovellus/tarkastusajo-id
       :tarkastuspisteet-atom sovellus/kirjauspisteet
       :soratiemittaussyotto-atom sovellus/soratiemittaussyotto
       :mittaustyyppi-atom sovellus/mittaustyyppi
       :jatkuvat-havainnot-atom sovellus/jatkuvat-havainnot})
    (tr-haku/alusta-tr-haku sovellus/sijainti sovellus/tr-tiedot)))

(defn- tarkkaile-alustusta []
  (run! (when @sovellus/alustus-valmis?
          (after-delay 1000
            (reset! sovellus/sovelluksen-naytto-sallittu? true)))))

(defn- arsyttava-virhe [& msgs]
  (.alert js/window (str "Upsista keikkaa, Harja räsähti! Olemme pahoillamme. Kuulisimme "
                         "mielellämme miten sait vian esiin, joten voisitko lähettää meille "
                         "palautetta? Liitä mukaan alla olevat virheen tekniset tiedot, "
                         "kuvankaappaus sekä kuvaus siitä mitä olit tekemässä.\n"
                         (apply str msgs))))

(defn- kuuntele-rasahdyksia []
  (set! (.-onerror js/window)
        (fn [errorMsg url lineNumber column errorObj]
          (.error js/console errorObj)
          (arsyttava-virhe errorMsg " " url " " lineNumber ":" column " " errorObj))))

(defn main []
  (esta-mobiililaitteen-nayton-lukitus)
  (alusta-paikannus-id)
  (tarkkaile-alustusta)
  (alusta-geolokaatio-api)
  (kuuntele-dom-eventteja)
  (kuuntele-rasahdyksia)
  (kuuntele-sivun-nakyvyytta sovellus/tarkastusajo-kaynnissa?
                             sovellus/kuvaa-otetaan?)
  (alusta-sovellus))

;; --- Testausapurit ---

(def kaynissa-oleva-simulaatio-id (atom nil))
(def +oletuspaivitysvali+ 2000)
(def +oletustarkkuus+ 5)

(defn ^:export aja-testireitti
  "Hakee kannasta annetun tarkastusajon id:n ja ajaa sen.

   Päivitysväli kertoo, kuinka tiheästi siirrytään seuraavaan pisteeseen (ms).
   Arvo 2000 vastaa suurin piirtein todellista ajonopeutta.

   Tarkkuus on sama kuin HTML5 Geolocation API:n palauttama (säde metreinä).
   Esim. 5 on hyvin tarkka paikannus ja 50 epätarkka."
  ([] (aja-testireitti 1 +oletuspaivitysvali+ +oletustarkkuus+))
  ([tarkastusajo-id] (aja-testireitti tarkastusajo-id +oletuspaivitysvali+ +oletustarkkuus+))
  ([tarkastusajo-id paivitysvali] (aja-testireitti tarkastusajo-id paivitysvali +oletustarkkuus+))
  ([tarkastusajo-id paivitysvali tarkkuus]
   (.log js/console "Käynnistetään simuloidun reitin ajaminen")
   (paikannus/lopeta-paikannus @paikannus-id)
   (go
     (let [tama-simulaatio-id (hash (l/local-now))
           vastaus (<! (comms/hae-simuloitu-tarkastusajo! tarkastusajo-id))
           sijainnit (:ok vastaus)]
       (when (and sijainnit (> (count sijainnit) 0))
         (.log js/console "Ajetaan testireitti, jossa " (count sijainnit) " sijaintia")
         (reset! sovellus/keskita-ajoneuvoon? true)
         (reset! kaynissa-oleva-simulaatio-id tama-simulaatio-id)
         (loop [sijainti-indeksi 0]
           (.log js/console (str "Simuloidaan sijainti (indeksi: " sijainti-indeksi ")"))
           (let [sijainti (nth sijainnit sijainti-indeksi)]
             (paikannus/aseta-testisijainti sovellus/sijainti (:sijainti sijainti) tarkkuus)
             (<! (async/timeout paivitysvali))
             (when (and (= tama-simulaatio-id @kaynissa-oleva-simulaatio-id)
                        (< sijainti-indeksi (- (count sijainnit) 1)))
               (recur (inc sijainti-indeksi))))))))))
