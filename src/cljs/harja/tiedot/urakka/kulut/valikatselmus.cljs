(ns harja.tiedot.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.urakka :as urakka]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.urakka.urakka :as tila]))

(defrecord TallennaOikaisu [oikaisu id])
(defrecord TallennaOikaisuOnnistui [vastaus id])
(defrecord TallennaOikaisuEpaonnistui [vastaus])
(defrecord PoistaOikaisu [oikaisu muokkaa!])
(defrecord PoistaOikaisuOnnistui [vastaus muokkaa!])
(defrecord PoistaOikaisuEpaonnistui [vastaus])
(defrecord LisaaOikaisu [])
(defrecord PaivitaPaatosLomake [tiedot paatos ylitys-tai-alitus-maara])

(defn- validoi-paatoslomake [tiedot ylitys-tai-alitus-maara] (apply tila/luo-validius-tarkistukset
                                                                    [[:maksu] [tila/ei-nil
                                                                               tila/ei-tyhja
                                                                               #(if (and (= :prosentti (:euro-vai-prosentti tiedot))
                                                                                         (< 100 %))
                                                                                  nil
                                                                                  %)
                                                                               #(if (and (= :euro (:euro-vai-prosentti tiedot))
                                                                                         (< ylitys-tai-alitus-maara %))
                                                                                  nil
                                                                                  %)]]))

(extend-protocol tuck/Event
  TallennaOikaisu
  (process-event [{oikaisu :oikaisu id :id} app]
    (tuck-apurit/post! :tallenna-tavoitehinnan-oikaisu
                       oikaisu
                       {:onnistui ->TallennaOikaisuOnnistui
                        :onnistui-parametrit [id]
                        :epaonnistui ->TallennaOikaisuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  TallennaOikaisuOnnistui
  (process-event [{vastaus :vastaus id :id} app]
    (let [oikaisut-atom (:tavoitehinnan-oikaisut-atom app)]
      (when (map? vastaus)
        ;; Uusi oikaisu luotu
        (swap! oikaisut-atom assoc id vastaus))
      (viesti/nayta-toast! "Oikaisu tallennettu")
      app))

  TallennaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app)

  PoistaOikaisu
  (process-event [{oikaisu :oikaisu muokkaa! :muokkaa!} app]
    (let [oikaisu (assoc oikaisu ::muokkaustiedot/poistettu? true)]
      (if (:ei-palvelimella? oikaisu)
        (muokkaa! assoc :poistettu true)
        (tuck-apurit/post! :tallenna-tavoitehinnan-oikaisu
                           oikaisu
                           {:onnistui ->PoistaOikaisuOnnistui
                            :epaonnistui ->PoistaOikaisuEpaonnistui
                            :onnistui-parametrit [muokkaa!]
                            :paasta-virhe-lapi? true}))
      app))

  PoistaOikaisuOnnistui
  (process-event [{vastaus :vastaus muokkaa! :muokkaa!} app]
    (do
      (muokkaa! assoc :poistettu true)
      (viesti/nayta-toast! "Oikaisu poistettu")
      app))


  PoistaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  LisaaOikaisu
  (process-event [_ app]
    (let [oikaisut @(:tavoitehinnan-oikaisut-atom app)
          uusi-indeksi (inc (apply max (keys oikaisut)))
          urakka-id (-> @tila/yleiset :urakka :id)]
      (println uusi-indeksi)
      (swap! (:tavoitehinnan-oikaisut-atom app) conj
             {uusi-indeksi {:harja.domain.muokkaustiedot/poistettu? false
                            :harja.domain.kulut.valikatselmus/otsikko ""
                            :harja.domain.kulut.valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
                            :harja.domain.kulut.valikatselmus/selite ""
                            :harja.domain.kulut.valikatselmus/summa nil
                            ::urakka/id urakka-id
                            :ei-palvelimella? true}}))
    app)

  PaivitaPaatosLomake
  (process-event [{tiedot :tiedot paatos :paatos ylitys-tai-alitus-maara :ylitys-tai-alitus-maara} app]
    (let [{:keys [validoi] :as validoinnit} (validoi-paatoslomake tiedot ylitys-tai-alitus-maara)
          {:keys [validi? validius]} (validoi validoinnit tiedot)]
      (do
        (-> app
            (assoc paatos tiedot)
            (assoc-in [paatos ::tila/validius] validius)
            (assoc-in [paatos ::tila/validi?] validi?))))))