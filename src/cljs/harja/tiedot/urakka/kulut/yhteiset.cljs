(ns harja.tiedot.urakka.kulut.yhteiset
  (:require
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.tiedot.urakka :as urakka-tiedot]
    [harja.pvm :as pvm]
    [harja.tiedot.urakka.urakka :as tila]
    [tuck.core :as tuck]))

(def manuaalisen-kattohinnan-syoton-vuodet
  [2019 2020])

(defn oikaisujen-summa [oikaisut hoitokauden-alkuvuosi]
  (or (apply + (map ::valikatselmus/summa (filter
                                            #(and (not (or (:poistettu %) (::muokkaustiedot/poistettu? %))))
                                            (-> oikaisut (get hoitokauden-alkuvuosi) vals)))) 0))

(defn budjettitavoite-valitulle-hoitokaudelle [app]
  (some->>
    app
    :budjettitavoite
    (filter #(= (:hoitokauden-alkuvuosi app) (:hoitokauden-alkuvuosi %)))
    first))

(defn oikaistu-tavoitehinta-valitulle-hoitokaudelle [app]
  (-> app budjettitavoite-valitulle-hoitokaudelle :tavoitehinta-oikaistu))

(defn hoitokauden-tavoitehinta 
  ([hoitokauden-nro app oletusarvo]
   (let [tavoitehinta 
         (some #(when (= hoitokauden-nro (:hoitokausi %))
                  (let [indeksikorjattu? (pos? (:tavoitehinta-indeksikorjattu %))] 
                    {:tavoitehinta (or (if indeksikorjattu?
                                         (:tavoitehinta-indeksikorjattu %)
                                         (:tavoitehinta %))
                                       oletusarvo)
                     :indeksikorjattu? indeksikorjattu?}))
               (:budjettitavoite app))]
     tavoitehinta))
  ([hoitokauden-nro app]
   (hoitokauden-tavoitehinta hoitokauden-nro app nil)))

(defn hoitokauden-tavoitehinta-vuodelle [hoitokauden-alkuvuosi app]
  (let [tavoitehinta (some #(when (= hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi %))
                              (:tavoitehinta-indeksikorjattu %))
                       (:budjettitavoite app))]
    tavoitehinta))

(defn hoitokauden-oikaistu-tavoitehinta [hoitokauden-nro app]
  (let [tavoitehinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                              (:tavoitehinta-oikaistu %))
                       (:budjettitavoite app))]
    tavoitehinta))

(defn hoitokauden-kattohinta 
  ([hoitokauden-nro app oletusarvo]
   (let [kattohinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                             (let [indeksikorjattu? (pos? (:kattohinta-indeksikorjattu %))]
                               {:kattohinta (or (if indeksikorjattu?
                                                  (:kattohinta-indeksikorjattu %)
                                                  (:kattohinta %))
                                                oletusarvo)
                                :indeksikorjattu? indeksikorjattu?}))
                          (:budjettitavoite app))]
     kattohinta))
  ([hoitokauden-nro app]
   (hoitokauden-kattohinta hoitokauden-nro app nil)))

(defn hoitokauden-oikaistu-kattohinta [hoitokauden-nro app]
  (let [kattohinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                            (:kattohinta-oikaistu %))
                     (:budjettitavoite app))]
    kattohinta))

(defn valikatselmus-tekematta? [app]
  (let [valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        valittu-hoitovuosi-nro (urakka-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        {:keys [tavoitehinta]} (hoitokauden-tavoitehinta valittu-hoitovuosi-nro app 0)
        kattohinta (or (hoitokauden-oikaistu-kattohinta valittu-hoitovuosi-nro app) 0)
        toteuma (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        oikaisujen-summa (oikaisujen-summa (:tavoitehinnan-oikaisut app) valittu-hoitokauden-alkuvuosi)
        oikaistu-tavoitehinta (+ tavoitehinta oikaisujen-summa)
        oikaistu-kattohinta (+ kattohinta oikaisujen-summa)
        urakan-paatokset (:urakan-paatokset app)
        filtteroi-paatos-fn (fn [paatoksen-tyyppi]
                              (first (filter #(and (= (::valikatselmus/hoitokauden-alkuvuosi %) valittu-hoitokauden-alkuvuosi)
                                                (= (::valikatselmus/tyyppi %) (name paatoksen-tyyppi))) urakan-paatokset)))
        tavoitehinta-alitettu? (> oikaistu-tavoitehinta toteuma)
        tavoitehinta-ylitetty? (> toteuma oikaistu-tavoitehinta)
        kattohinta-ylitetty? (> toteuma oikaistu-kattohinta)
        tavoitehinnan-alitus-paatos (filtteroi-paatos-fn :tavoitehinnan-alitus)
        tavoitehinnan-ylitys-paatos (filtteroi-paatos-fn :tavoitehinnan-ylitys)
        kattohinnan-ylitys-paatos (filtteroi-paatos-fn :kattohinnan-ylitys)
        lupaus-bonus-paatos (filtteroi-paatos-fn :lupaus-bonus)
        lupaus-sanktio-paatos (filtteroi-paatos-fn :lupaus-sanktio)]
    (and
      (<= valittu-hoitokauden-alkuvuosi (pvm/vuosi (pvm/nyt)))
      (or
        (and tavoitehinta-alitettu? (nil? tavoitehinnan-alitus-paatos))
        (and tavoitehinta-ylitetty? (nil? tavoitehinnan-ylitys-paatos))
        (and kattohinta-ylitetty? (nil? kattohinnan-ylitys-paatos))
        (and (nil? lupaus-sanktio-paatos) (nil? lupaus-bonus-paatos))))))

(defrecord NollaaValikatselmuksenPaatokset [])

(extend-protocol tuck/Event

  NollaaValikatselmuksenPaatokset
  (process-event [_ app]
    (dissoc app :tavoitehinnan-ylitys-lomake :tavoitehinnan-alitus-lomake :kattohinnan-ylitys-lomake :lupaus-bonus-lomake :lupaus-sanktio-lomake :kattohinnan-oikaisu)))