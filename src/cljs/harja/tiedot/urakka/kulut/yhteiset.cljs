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

(defn hoitokauden-tavoitehinta [hoitokauden-nro app]
  (let [tavoitehinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                              (if (pos? (:tavoitehinta-indeksikorjattu %))
                                (:tavoitehinta-indeksikorjattu %)
                                (:tavoitehinta %)))
                       (:budjettitavoite app))]
    tavoitehinta))

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

(defn hoitokauden-kattohinta [hoitokauden-nro app]
  (let [kattohinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                            (if (pos? (:kattohinta-indeksikorjattu %))
                              (:kattohinta-indeksikorjattu %)
                              (:kattohinta %)))
                     (:budjettitavoite app))]
    kattohinta))

(defn hoitokauden-oikaistu-kattohinta [hoitokauden-nro app]
  (let [kattohinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                            (:kattohinta-oikaistu %))
                     (:budjettitavoite app))]
    kattohinta))

(defn valikatselmus-tekematta? [app]
  (let [valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        valittu-hoitovuosi-nro (urakka-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        tavoitehinta (or (hoitokauden-tavoitehinta valittu-hoitovuosi-nro app) 0)
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
        lupausbonus-paatos (filtteroi-paatos-fn :lupaus-onus)
        lupaussanktio-paatos (filtteroi-paatos-fn :lupaussanktio)]
    (and
      (<= valittu-hoitokauden-alkuvuosi (pvm/vuosi (pvm/nyt)))
      (or
        (and tavoitehinta-alitettu? (nil? tavoitehinnan-alitus-paatos))
        (and tavoitehinta-ylitetty? (nil? tavoitehinnan-ylitys-paatos))
        (and kattohinta-ylitetty? (nil? kattohinnan-ylitys-paatos))
        (and (nil? lupaussanktio-paatos) (nil? lupausbonus-paatos))))))

(defrecord NollaaValikatselmuksenPaatokset [])

(extend-protocol tuck/Event

  NollaaValikatselmuksenPaatokset
  (process-event [_ app]
    (dissoc app :kattohinnan-ylitys-lomake :lupausbonus-lomake :lupaussanktio-lomake :kattohinnan-oikaisu)))