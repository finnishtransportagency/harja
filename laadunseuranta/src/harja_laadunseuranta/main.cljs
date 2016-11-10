(ns harja-laadunseuranta.main
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.ui.kartta :as kartta]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.ui.alustus :as alustus]
            [harja-laadunseuranta.ui.ylapalkki :as ylapalkki]
            [harja-laadunseuranta.ui.paanavigointi :refer [paanavigointi]]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.ui.tr-haku :as tr-haku]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.ui.havaintolomake :as havaintolomake]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.ui.tarkastusajon-luonti :as tarkastusajon-luonti]
            [harja-laadunseuranta.utils :refer [flip erota-mittaukset erota-havainnot]]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- peruuta-pikavalinta []
  (reset! s/pikavalinta nil)
  (reset! s/tr-alku nil)
  (reset! s/tr-loppu nil))

(defn- havaintolomake [tallennettu-fn peruutettu-fn]
  (let [model {:kayttajanimi @s/kayttajanimi
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
     [havaintolomake/havaintolomake asetukset/+wmts-url+
      asetukset/+wmts-url-kiinteistojaotus+ asetukset/+wmts-url-ortokuva+
      model
      #(do
        (peruuta-pikavalinta)
        (reitintallennus/kirjaa-kertakirjaus @s/idxdb % @s/tarkastusajo-id)
        (kartta/lisaa-kirjausikoni "!")
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
                                       @s/tarkastusajo-id))

(defn- laheta-kitkamittaus [arvo]
  (kartta/lisaa-kirjausikoni (str arvo))
  (kirjaa {:kitkamittaus arvo}))

(defn- laheta-lumisuus [arvo]
  (kartta/lisaa-kirjausikoni (str arvo))
  (reset! s/lumimaara nil)
  (kirjaa {:lumisuus arvo})
  (swap! s/havainnot assoc :lumista false))

(defn- laheta-tasaisuus [arvo]
  (kartta/lisaa-kirjausikoni (str arvo))
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

(defn- paanakyma []
  (let [alivalikot (atom {})]
    (fn []
      [:div.toplevel
       [ylapalkki/ylapalkki]

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

        (when @s/nayta-paanavigointi?
          [paanavigointi])

        [:div.paasisalto
         [ilmoitukset/ilmoituskomponentti s/ilmoitukset]

         ;; TODO Lomakkeet vaatii vielä säätämistä
         #_(when @s/kirjaamassa-havaintoa
           [havaintolomake sulje-havaintodialogi sulje-havaintodialogi])

         ;; TODO Lomakkeet vaatii vielä säätämistä
         #_(when @s/kirjaamassa-yleishavaintoa
           [havaintolomake yleishavainto-kirjattu sulje-yleishavaintodialogi])

         (when @s/tarkastusajo-paattymassa
           [:div.tarkastusajon-luonti-dialog-container
            [tarkastusajon-luonti/tarkastusajon-paattamisdialogi s/lahettamattomia-merkintoja]])

         (when (and @s/palautettava-tarkastusajo (not (= "?relogin=true" js/window.location.search)))
           [:div.tarkastusajon-luonti-dialog-container
            [tarkastusajon-luonti/tarkastusajon-jatkamisdialogi]])

         [spinneri s/lahettamattomia-merkintoja]
         [tr-haku/tr-selailukomponentti s/tr-tiedot-nakyvissa s/tr-tiedot]]]])))

(defn main []
  (if @s/sovellus-alustettu
    [paanakyma]
    [alustus/alustuskomponentti
     {:gps-tuettu s/gps-tuettu
      :ensimmainen-sijainti s/ensimmainen-sijainti
      :idxdb-tuettu s/idxdb
      :kayttaja s/kayttajanimi
      :verkkoyhteys s/verkkoyhteys
      :selain-tuettu s/selain-tuettu}]))
