(ns harja-laadunseuranta.main
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.kartta :as kartta]
            [harja-laadunseuranta.pikavalintapaneeli :as pikavalinnat]
            [harja-laadunseuranta.kuvat :as kuvat]
            [harja-laadunseuranta.sovellus :as s]
            [harja-laadunseuranta.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.alustus :as alustus]
            [harja-laadunseuranta.ylapalkki :as ylapalkki]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.comms :as comms]
            [harja-laadunseuranta.tr-haku :as tr-haku]
            [harja-laadunseuranta.puhe :as puhe]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.havaintolomake :as havaintolomake]
            [harja-laadunseuranta.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.tarkastusajon-luonti :as tarkastusajon-luonti]
            [harja-laadunseuranta.utils :refer [flip erota-mittaukset erota-havainnot]]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- luonti-peruttu []
  (reset! s/tallennustilaa-muutetaan false)
  (reset! s/tallennus-kaynnissa false))

(defn- luo-ajo [tarkastustyyppi]
  (go-loop []
    (if-let [id (-> (<! (comms/luo-ajo! tarkastustyyppi)) :ok :id)]
      (s/tarkastusajo-kayntiin! tarkastustyyppi id)
      ;; yritä uudleleen kunnes onnistuu
      (do (<! (timeout 1000))
          (recur)))))

(defn- paattaminen-peruttu []
  (reset! s/tallennus-kaynnissa true)
  (reset! s/tallennustilaa-muutetaan false))


(defn- paata-ajo []
  (go-loop []
    (if (<! (comms/paata-ajo! @s/tarkastusajo @s/valittu-urakka))
      (s/tarkastusajo-seis!)

      ;; yritä uudelleen kunnes onnistuu, spinneri pyörii
      (do (<! (timeout 1000))
          (recur)))))

(defn- lisaa-kirjausikoni [teksti]
  (swap! s/kirjauspisteet
         conj (assoc (select-keys (:nykyinen @s/sijainti) [:lat :lon])
                :label teksti)))

(defn- peruuta-pikavalinta []
  (reset! s/pikavalinta nil)
  (reset! s/tr-alku nil)
  (reset! s/tr-loppu nil))

(defn- havaintolomake [tallennettu-fn peruutettu-fn]
  (let [model {:kayttajanimi @s/kayttajanimi
               :tarkastustyyppi @s/tarkastustyyppi
               :tr-osoite (utils/unreactive-deref s/tr-osoite)
               :tr-alku @s/tr-alku
               :tr-loppu @s/tr-loppu
               :aikaleima (l/local-now)
               :havainnot (erota-havainnot @s/havainnot)
               :pikavalinnan-kuvaus (@s/vakiohavaintojen-kuvaukset @s/pikavalinta)
               :pikavalinta @s/pikavalinta
               :mittaukset {}
               :kitkan-keskiarvo @s/kitkan-keskiarvo
               :lumisuus @s/lumimaara
               :tasaisuus @s/tasaisuus
               :sijainti (:nykyinen (utils/unreactive-deref s/sijainti))
               :laadunalitus? false
               :kuvaus ""
               :kuva nil}]
    [:div.havaintolomake-container
     [havaintolomake/havaintolomake asetukset/+wmts-url+ asetukset/+wmts-url-kiinteistojaotus+ asetukset/+wmts-url-ortokuva+
      model
      #(do
        (peruuta-pikavalinta)
        (reitintallennus/kirjaa-kertakirjaus @s/idxdb % @s/tarkastusajo)
        (lisaa-kirjausikoni "!")
        (tallennettu-fn))
      #(do
        (peruuta-pikavalinta)
        (peruutettu-fn))]]))

(defn- spinneri [lahettamattomia]
  (when (> @lahettamattomia 0)
    [:img.spinner {:src kuvat/+spinner+}]))

(defn- kirjaa [arvot]
  (reitintallennus/kirjaa-kertakirjaus @s/idxdb
                                       {:aikaleima (l/local-now)
                                        :mittaukset (merge (erota-mittaukset @s/havainnot) arvot)
                                        :havainnot (erota-havainnot @s/havainnot)
                                        :sijainti (:nykyinen @s/sijainti)}
                                       @s/tarkastusajo))

(defn- laheta-kitkamittaus [arvo]
  (lisaa-kirjausikoni (str arvo))
  (kirjaa {:kitkamittaus arvo}))

(defn- laheta-lumisuus [arvo]
  (lisaa-kirjausikoni (str arvo))
  (reset! s/lumimaara nil)
  (kirjaa {:lumisuus arvo})
  (swap! s/havainnot assoc :lumista false))

(defn- laheta-tasaisuus [arvo]
  (lisaa-kirjausikoni (str arvo))
  (reset! s/tasaisuus nil)
  (kirjaa {:tasaisuus arvo})
  (swap! s/havainnot assoc :tasauspuute false))

(defn- laheta-soratiehavainto [tasaisuus kiinteys polyavyys]
  (kirjaa {:tasaisuus tasaisuus
           :kiinteys kiinteys
           :polyavyys polyavyys})
  (reset! s/tasaisuus nil)
  (reset! s/polyavyys nil)
  (reset! s/kiinteys nil)
  (swap! s/havainnot assoc :soratie false))

(defn- laheta-kertakirjaus [kirjaus]
  (reset! s/pikavalinta kirjaus)
  (reset! s/kirjaamassa-havaintoa true))

(defn- laheta-yleishavainto [havainnot]
  (reset! s/kirjaamassa-yleishavaintoa true))


(defn- sulje-havaintodialogi []
  (reset! s/kirjaamassa-havaintoa false))

(defn- sulje-yleishavaintodialogi []
  (reset! s/kirjaamassa-yleishavaintoa false)
  #_(reset! s/yleishavainto-kaynnissa false))

(defn- yleishavainto-kirjattu []
  (reset! s/kirjaamassa-yleishavaintoa false)
  (reset! s/yleishavainto-kaynnissa false))

(defn- jatka-ajoa []
  (let [ajo @s/palautettava-tarkastusajo]
    (js/console.log "Tarkastusajo palautetaan: " (pr-str ajo))
    (reset! s/reittipisteet (mapv utils/keywordize-map (js->clj (get ajo "reittipisteet"))))
    (reset! s/kirjauspisteet (mapv utils/keywordize-map (js->clj (get ajo "tarkastuspisteet"))))
    (reset! s/tarkastustyyppi (keyword (get ajo "tarkastustyyppi")))
    (reset! s/tarkastusajo (get ajo "tarkastusajo"))
    (reset! s/tallennus-kaynnissa true))
  (reset! s/palautettava-tarkastusajo nil))

(defn- pakota-ajon-lopetus []
  (let [ajo @s/palautettava-tarkastusajo]
    (reitintallennus/poista-tarkastusajo @s/idxdb (get ajo "tarkastusajo"))
    (reitintallennus/tyhjenna-reittipisteet @s/idxdb))
  (reset! s/palautettava-tarkastusajo nil))

(defn- paanakyma []
  (let [alivalikot (atom {})]
    (fn []
      [:div.toplevel
       [ylapalkki/ylapalkkikomponentti
        {:tiedot-nakyvissa s/tr-tiedot-nakyvissa
         :hoitoluokka s/hoitoluokka
         :soratiehoitoluokka s/soratiehoitoluokka
         :tr-osoite s/tr-osoite
         :kiinteistorajat s/nayta-kiinteistorajat
         :ortokuva s/nayta-ortokuva
         :tallennus-kaynnissa s/tallennus-kaynnissa
         :tallennustilaa-muutetaan s/tallennustilaa-muutetaan
         :keskita-ajoneuvoon s/keskita-ajoneuvoon
         :disabloi-kaynnistys? (or @s/kirjaamassa-havaintoa @s/kirjaamassa-yleishavaintoa s/palautettava-tarkastusajo)
         :valittu-urakka s/valittu-urakka
         :palvelinvirhe s/palvelinvirhe}]

       [:div.paasisalto-container
        [kartta/karttakomponentti
         {:wmts-url asetukset/+wmts-url+
          :wmts-url-kiinteistorajat asetukset/+wmts-url-kiinteistojaotus+
          :wmts-url-ortokuva asetukset/+wmts-url-ortokuva+
          :sijainti-atomi s/kartan-keskipiste
          :ajoneuvon-sijainti-atomi s/ajoneuvon-sijainti
          :reittipisteet-atomi s/reittipisteet
          :kirjauspisteet-atomi s/kirjauspisteet
          :optiot s/karttaoptiot}]

        [:div.paasisalto
         [ilmoitukset/ilmoituskomponentti s/ilmoitukset]

         (when @s/kirjaamassa-havaintoa
           [havaintolomake sulje-havaintodialogi sulje-havaintodialogi])

         (when @s/kirjaamassa-yleishavaintoa
           [havaintolomake yleishavainto-kirjattu sulje-yleishavaintodialogi])

         (when @s/tarkastusajo-luotava
           [:div.tarkastusajon-luonti-dialog-container
            [tarkastusajon-luonti/tarkastusajon-luontidialogi luo-ajo luonti-peruttu]])

         (when @s/tarkastusajo-paattymassa
           [:div.tarkastusajon-luonti-dialog-container
            [tarkastusajon-luonti/tarkastusajon-paattamisdialogi s/lahettamattomia paata-ajo paattaminen-peruttu]])

         (when (and @s/palautettava-tarkastusajo (not (= "?relogin=true" js/window.location.search)))
           [:div.tarkastusajon-luonti-dialog-container
            [tarkastusajon-luonti/tarkastusajon-jatkamisdialogi jatka-ajoa pakota-ajon-lopetus]])

         [spinneri s/lahettamattomia]

         [tr-haku/tr-selailukomponentti s/tr-tiedot-nakyvissa s/tr-tiedot]

         (when @s/nayta-sivupaneeli
           [:div
            [:div.sivupaneeli-container
             [pikavalinnat/pikavalintapaneeli s/tr-osoite s/tarkastustyyppi s/havainnot alivalikot
              laheta-kitkamittaus laheta-kertakirjaus laheta-yleishavainto laheta-lumisuus laheta-tasaisuus laheta-soratiehavainto
              s/kitkan-keskiarvo s/lumimaara s/tasaisuus s/kiinteys s/polyavyys]]
            [:div.pikavalinta-footer
             (when-not (or @s/kirjaamassa-yleishavaintoa
                           @s/kirjaamassa-havaintoa)
               [pikavalinnat/lisaa-havainto @s/yleishavainto-kaynnissa
                #(if %
                  (do
                    (reset! s/tr-loppu @s/tr-osoite)
                    (reset! s/kirjaamassa-yleishavaintoa true))
                  (reset! s/kirjaamassa-havaintoa true))
                #(do
                  (reset! s/tr-alku @s/tr-osoite)
                  (reset! s/yleishavainto-kaynnissa true))])]])]]])))

(defn main []
  (if @s/sovellus-alustettu
    [paanakyma]
    [alustus/alustuskomponentti s/gps-tuettu s/idxdb
     s/tarkastustyyppi s/tarkastusajo s/kayttajanimi]))
