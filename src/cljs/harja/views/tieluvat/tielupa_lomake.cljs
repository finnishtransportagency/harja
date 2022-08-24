(ns harja.views.tieluvat.tielupa-lomake
  (:require
    [tuck.core :refer [tuck]]
    [harja.loki :refer [log]]
    [clojure.string :as str]
    [harja.pvm :as pvm]
    [harja.fmt :as fmt]
    [harja.ui.liitteet :as liitteet]
    [reagent.core :as r :refer [atom]]
    [clojure.set :as set]
    [harja.domain.tielupa :as tielupa]

    [harja.tiedot.tieluvat.tielupa-tiedot :as tiedot]


    [harja.ui.lomake :as lomake]
    [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
    [harja.ui.grid :as grid]
    [harja.ui.debug :as debug]
    [harja.ui.valinnat :as valinnat]
    [harja.ui.kentat :as kentat]
    [harja.ui.napit :as napit]
    [harja.views.kartta :as kartta]
    [harja.views.kartta.tasot :as tasot])
  (:require-macros
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn nayta-sijaintigrid? [valittu-tielupa]
  (not-empty (tiedot/pelkat-vapaat-sijainnit valittu-tielupa)))

(defn tr-grid-kentat []
  [{:otsikko "Tie\u00ADre\u00ADkis\u00ADte\u00ADri\u00ADo\u00ADsoi\u00ADte"
    :leveys 5
    :hae #(-> %
            ((juxt ::tielupa/tie
               ::tielupa/aosa
               ::tielupa/aet
               ::tielupa/losa
               ::tielupa/let))
            (->>
              (keep identity)
              (str/join "/")))}
   {:otsikko "A\u00ADjo\u00ADra\u00ADta"
    :leveys 1
    :nimi ::tielupa/ajorata}
   {:otsikko "Kai\u00ADsta"
    :leveys 1
    :nimi ::tielupa/kaista}
   {:otsikko "Puo\u00ADli"
    :leveys 1
    :nimi ::tielupa/puoli}
   {:otsikko "Kart\u00ADta\u00ADpvm"
    :leveys 5
    :tyyppi :pvm-aika
    :fmt pvm/pvm-aika-opt
    :nimi ::tielupa/karttapvm}])

(defn sijaintien-lomakegrid [valittu-tielupa]
  [grid/grid
   {:tyhja "Ei sijaintietoja"
    :tunniste identity}
   (tr-grid-kentat)
   (sort-by
     (juxt ::tielupa/tie
       ::tielupa/aosa
       ::tielupa/aet
       ::tielupa/losa
       ::tielupa/let
       ::tielupa/ajorata
       ::tielupa/kaista
       ::tielupa/puoli)
     (or (tiedot/pelkat-vapaat-sijainnit valittu-tielupa) []))])

(defn johtoasennusten-lomakegrid [valittu-tielupa]
  [grid/grid
   {:tyhja "Ei johtoasennuksia"
    :tunniste identity}
   (concat
     [{:otsikko "Lai\u00ADte"
       :leveys 6
       :nimi ::tielupa/laite}
      {:otsikko "A\u00ADsen\u00ADnus\u00ADtyyp\u00ADpi"
       :leveys 6
       :nimi ::tielupa/asennustyyppi}
      {:otsikko "Kom\u00ADmen\u00ADtit"
       :leveys 10
       :nimi ::tielupa/kommentit}]
     (tr-grid-kentat))
   (sort-by
     (juxt ::tielupa/laite
       ::tielupa/asennustyyppi
       ::tielupa/tie
       ::tielupa/aosa
       ::tielupa/aet
       ::tielupa/losa
       ::tielupa/let
       ::tielupa/ajorata
       ::tielupa/kaista
       ::tielupa/puoli)
     (or (::tielupa/johtoasennukset valittu-tielupa) []))])

(defn mainosten-lomakegrid [valittu-tielupa]
  [grid/grid
   {:tyhja "Ei mainoksia"
    :tunniste identity}
   (tr-grid-kentat)
   (sort-by
     (juxt ::tielupa/tie
       ::tielupa/aosa
       ::tielupa/aet
       ::tielupa/losa
       ::tielupa/let
       ::tielupa/ajorata
       ::tielupa/kaista
       ::tielupa/puoli)
     (or (::tielupa/mainokset valittu-tielupa) []))])

(defn opasteiden-lomakegrid [valittu-tielupa]
  [grid/grid
   {:tyhja "Ei opasteita"
    :tunniste identity}
   (concat
     [{:otsikko "Tu\u00ADlos\u00ADte\u00ADnu\u00ADme\u00ADro"
       :leveys 3
       :nimi ::tielupa/tulostenumero}
      {:otsikko "Ku\u00ADva\u00ADus"
       :leveys 10
       :nimi ::tielupa/kuvaus}]
     (tr-grid-kentat))
   (sort-by
     (juxt ::tielupa/tulostenumero
       ::tielupa/tie
       ::tielupa/aosa
       ::tielupa/aet
       ::tielupa/losa
       ::tielupa/let
       ::tielupa/ajorata
       ::tielupa/kaista
       ::tielupa/puoli)
     (or (::tielupa/opasteet valittu-tielupa) []))])

(defn liikennemerkkijarjestelyjen-lomakegrid [valittu-tielupa]
  [grid/grid
   {:tyhja "Ei liikennemerkkijärjestelyitä"
    :tunniste identity}
   (concat
     [{:otsikko "Lii\u00ADken\u00ADne\u00ADmerk\u00ADki"
       :leveys 3
       :nimi ::tielupa/liikennemerkki}
      {:otsikko "Al\u00ADku\u00ADpe\u00ADräi\u00ADnen no\u00ADpe\u00ADus\u00ADra\u00ADjoi\u00ADtus"
       :leveys 3
       :nimi ::tielupa/alkuperainen-nopeusrajoitus}
      {:otsikko "A\u00ADlen\u00ADnet\u00ADtu no\u00ADpe\u00ADus\u00ADra\u00ADjoi\u00ADtus"
       :leveys 3
       :nimi ::tielupa/alennettu-nopeusrajoitus}
      {:otsikko "No\u00ADpe\u00ADus\u00ADra\u00ADjoi\u00ADtuk\u00ADsen pi\u00ADtuus"
       :leveys 3
       :nimi ::tielupa/nopeusrajoituksen-pituus}]
     (tr-grid-kentat))

   (sort-by
     (juxt ::tielupa/tie
       ::tielupa/aosa
       ::tielupa/aet
       ::tielupa/losa
       ::tielupa/let
       ::tielupa/ajorata
       ::tielupa/kaista
       ::tielupa/puoli)
     (or (::tielupa/liikennemerkkijarjestelyt valittu-tielupa) []))])

(defn kaapelilupien-lomakegrid [valittu-tielupa]
  [grid/grid
   {:tyhja "Ei kaapeliasennuksia"
    :tunniste identity}
   (concat
     [{:otsikko "Lai\u00ADte"
       :leveys 6
       :nimi  ::tielupa/laite}
      {:otsikko "A\u00ADsen\u00ADnus\u00ADtyyp\u00ADpi"
       :leveys 6
       :nimi  ::tielupa/asennustyyppi}
      {:otsikko "Kom\u00ADmen\u00ADtit"
       :leveys 10
       :nimi  ::tielupa/kommentit}
      {:otsikko "Maa\u00ADkaa\u00ADpe\u00ADli\u00ADa met\u00ADreis\u00ADsä"
       :leveys 3
       :nimi  ::tielupa/maakaapelia-metreissa}
      {:otsikko "Il\u00ADma\u00ADkaa\u00ADpe\u00ADli\u00ADa met\u00ADreis\u00ADsä"
       :leveys 3
       :nimi  ::tielupa/ilmakaapelia-metreissa}
      {:otsikko "No\u00ADpe\u00ADus\u00ADra\u00ADjoi\u00ADtus"
       :leveys 2
       :nimi  ::tielupa/nopeusrajoitus}
      {:otsikko "Lii\u00ADken\u00ADne\u00ADmää\u00ADrä"
       :leveys 2
       :nimi  ::tielupa/liikennemaara}]
     (tr-grid-kentat))
   (sort-by
     (juxt ::tielupa/laite
       ::tielupa/asennustyyppi
       ::tielupa/tie
       ::tielupa/aosa
       ::tielupa/aet
       ::tielupa/losa
       ::tielupa/let
       ::tielupa/ajorata
       ::tielupa/kaista
       ::tielupa/puoli)
     (or (::tielupa/kaapeliasennukset valittu-tielupa) []))])

(defn hakijan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Hakijan tiedot"}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::tielupa/hakija-nimi}
    {:otsikko "Osasto"
     :tyyppi :string
     :nimi ::tielupa/hakija-osasto}
    {:otsikko "Postinosoite"
     :tyyppi :string
     :nimi ::tielupa/hakija-postinosoite}
    {:otsikko "Postinumero"
     :tyyppi :string
     :nimi ::tielupa/hakija-postinumero}
    {:otsikko "Puhelinnumero"
     :tyyppi :string
     :nimi ::tielupa/hakija-puhelinnumero}
    {:otsikko "Sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/hakija-sahkopostiosoite}
    {:otsikko "Tyyppi"
     :tyyppi :string
     :nimi ::tielupa/hakija-tyyppi}
    {:otsikko "Maakoodi"
     :tyyppi :string
     :nimi ::tielupa/hakija-maakoodi}))

(defn urakoitsijan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Urakoitsijan tiedot"}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::tielupa/urakoitsija-nimi}
    {:otsikko "Yhteyshenkilö"
     :tyyppi :string
     :nimi ::tielupa/urakoitsija-yhteyshenkilo}
    {:otsikko "Puhelinnumero"
     :tyyppi :string
     :nimi ::tielupa/urakoitsija-puhelinnumero}
    {:otsikko "Sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/urakoitsija-sahkopostiosoite}))

(def nayta-urakoitsijan-lomakekentat? (partial tiedot/nayta-kentat? urakoitsijan-lomakekentat))

(defn liikenneohjaajan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Liikenneohjaajan tiedot"}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::tielupa/liikenneohjaajan-nimi}
    {:otsikko "Yhteyshenkilö"
     :tyyppi :string
     :nimi ::tielupa/liikenneohjaajan-yhteyshenkilo}
    {:otsikko "Puhelinnumero"
     :tyyppi :string
     :nimi ::tielupa/liikenneohjaajan-puhelinnumero}
    {:otsikko "Sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/liikenneohjaajan-sahkopostiosoite}))

(def nayta-liikenneohjaajan-lomakekentat? (partial tiedot/nayta-kentat? liikenneohjaajan-lomakekentat))

(defn tienpitoviranomaisen-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Tienpitoviranomaisen tiedot"}
    {:otsikko "Puhelinnumero"
     :tyyppi :string
     :nimi ::tielupa/tienpitoviranomainen-puhelinnumero}
    {:otsikko "Sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/tienpitoviranomainen-sahkopostiosoite}
    {:otsikko "Lupapäällikkö"
     :tyyppi :string
     :nimi ::tielupa/tienpitoviranomainen-lupapaallikko}
    {:otsikko "Käsittelijä"
     :tyyppi :string
     :nimi ::tielupa/tienpitoviranomainen-kasittelija}))

(def nayta-tienpitoviranomaisen-lomakekentat? (partial tiedot/nayta-kentat? tienpitoviranomaisen-lomakekentat))

(def nayta-hakijan-lomakekentat? (partial tiedot/nayta-kentat? hakijan-lomakekentat))

(defn liikennemerkkijarjestelyn-lomakekentat [valittu-tielupa]
  nil #_ (lomake/ryhma
    {:otsikko "Liikennemerkkijärjestely"}
    {:otsikko "Aihe"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-aihe}
    {:otsikko "Sijainnin kuvaus"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-sijainnin-kuvaus}
    {:otsikko "Tapahtuman tiedot"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-tapahtuman-tiedot}
    {:otsikko "Nopeusrajoituksen syy"
     :nimi ::tielupa/liikennemerkkijarjestely-nopeusrajoituksen-syy}
    {:otsikko "Lisätiedot nopeusrajoituksesta"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-lisatiedot-nopeusrajoituksesta}
    {:otsikko "Muut liikennemerkit"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-muut-liikennemerkit}
    {:otsikko (str "Liikennemerkkijärjestelyt " (count (::tielupa/liikennemerkkijarjestelyt valittu-tielupa)) "kpl")
     :tyyppi :komponentti
     :nimi ::tielupa/liikennemerkkijarjestelyt
     :palstoja 3
     :komponentti (fn [{data :data}]
                    [liikennemerkkijarjestelyjen-lomakegrid data])}))

(def nayta-liikennemerkkijarjestelyn-lomakekentat? (partial tiedot/nayta-kentat? liikennemerkkijarjestelyn-lomakekentat))

(defn tyoluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Työlupa"}
    {:otsikko "Työn sisältö"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-tyon-sisalto}
    {:otsikko "Työn saa aloittaa"
     :tyyppi :pvm-aika
     :fmt pvm/pvm-aika-opt
     :nimi ::tielupa/tyolupa-tyon-saa-aloittaa}
    {:otsikko "Viimeistelty oltava"
     :tyyppi :pvm-aika
     :fmt pvm/pvm-aika-opt
     :nimi ::tielupa/tyolupa-viimeistely-oltava}
    {:otsikko "Ohjeet työn suorittamiseen"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-ohjeet-tyon-suorittamiseen}
    {:otsikko "Loppuosa puuttuu?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/tyolupa-los-puuttuu}
    {:otsikko "Ilmoitus tieliikennekeskukseen?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/tyolupa-ilmoitus-tieliikennekeskukseen}
    {:otsikko "Tilapäinen nopeusrajoitus?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/tyolupa-tilapainen-nopeusrajoitus}
    {:otsikko "Loppuosa, lisätiedot"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-los-lisatiedot}
    {:otsikko "Tieliikennekeskuksen sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-tieliikennekusksen-sahkopostiosoite}))

(def nayta-tyoluvan-lomakekentat? (partial tiedot/nayta-kentat? tyoluvan-lomakekentat))

(defn vesihuoltoluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Vesihuoltolupa"}
    {:otsikko "Tienylityksiä"
     :tyyppi :string
     :nimi ::tielupa/vesihuoltolupa-tienylityksia}
    {:otsikko "Tienalituksia"
     :tyyppi :string
     :nimi ::tielupa/vesihuoltolupa-tienalituksia}
    {:otsikko "Silta-asennuksia"
     :tyyppi :string
     :nimi ::tielupa/vesihuoltolupa-silta-asennuksia}
    {:otsikko (str "Johtoasennukset " (count (::tielupa/johtoasennukset valittu-tielupa)) "kpl")
     :tyyppi :komponentti
     :palstoja 3
     :nimi ::tielupa/johtoasennukset
     :komponentti (fn [{:keys [data]}]
                    [johtoasennusten-lomakegrid data])}))

(def nayta-vesihuoltoluvan-lomakekentat? (partial tiedot/nayta-kentat? vesihuoltoluvan-lomakekentat))

(defn valmistumisilmoituksen-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Valmistumisilmoitus"}
    {:otsikko "Valmistumisilmoitus vaaditaan?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/valmistumisilmoitus-vaaditaan}
    {:otsikko "Valmistumisilmoitus palautettu?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/valmistumisilmoitus-palautettu}
    {:otsikko "Valmistumisilmoitus"
     :tyyppi :string
     :nimi ::tielupa/valmistumisilmoitus}))

(def nayta-valmistumisilmoituksen-lomakekentat? (partial tiedot/nayta-kentat? valmistumisilmoituksen-lomakekentat))

(defn liittymaluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Liittymälupa"}
    {:otsikko "Myönnetty käyttötarkoitus"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-myonnetty-kayttotarkoitus}
    {:otsikko "Haettu käyttötarkoitus"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-haettu-kayttotarkoitus}
    {:otsikko "Liittymän siirto?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/liittymalupa-liittyman-siirto}
    {:otsikko "Tarkoituksen kuvaus"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-tarkoituksen-kuvaus}
    {:otsikko "Tilapäinen liittymälupa?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/liittymalupa-tilapainen}
    {:otsikko "Sijainnin kuvaus"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-sijainnin-kuvaus}
    {:otsikko "Arvioitu kokonaisliikenne"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-arvioitu-kokonaisliikenne}
    {:otsikko "Arvioitu kuorma-autoliikenne"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-arvioitu-kuorma-autoliikenne}
    {:otsikko "Nykyisen liittymän numero"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-nykyisen-liittyman-numero}
    {:otsikko "Nykyisen liittymän päiväys"
     :nimi ::tielupa/liittymalupa-nykyisen-liittyman-paivays
     :tyyppi :pvm-aika
     :fmt pvm/pvm-aika-opt}
    {:otsikko "Kiinteistö rekisterinumero"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-kiinteisto-rn}
    {:otsikko "Muut kulkuyhteydet"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-muut-kulkuyhteydet}
    {:otsikko "Valmistumisen takaraja"
     :tyyppi :pvm-aika
     :fmt pvm/pvm-aika-opt
     :nimi ::tielupa/liittymalupa-valmistumisen-takaraja}
    {:otsikko "Kylä"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-kyla}))

(def nayta-liittymaluvan-lomakekentat? (partial tiedot/nayta-kentat? liittymaluvan-lomakekentat))

(defn liittymaluvan-liittymaohjeen-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Liittymäluvan liittymäohje"}
    {:otsikko "Liittymäkaari"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-liittymakaari}
    {:otsikko "Leveys metreissä"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-leveys-metreissa}
    {:otsikko "Rumpu?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/liittymalupa-liittymaohje-rumpu}
    {:otsikko "Rummun halkaisija (mm)"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa}
    {:otsikko "Rummun etäisyys (m)"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-rummun-etaisyys-metreissa}
    {:otsikko "Odotustila (m)"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-odotustila-metreissa}
    {:otsikko "Näkemäpisteen etäisyys"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-nakemapisteen-etaisyys}
    {:otsikko "Liittymisnäkemä"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-liittymisnakema}
    {:otsikko "Liikennemerkit"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-liikennemerkit}
    {:otsikko "Lisäohjeet"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-lisaohjeet}))

(def nayta-liittymaluvan-liittymaohjeen-lomakekentat? (partial tiedot/nayta-kentat? liittymaluvan-liittymaohjeen-lomakekentat))

(defn johtoluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Johtolupa"}
    {:otsikko "Maakaapelia yhteensä"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-maakaapelia-yhteensa}
    {:otsikko "Ilmakaapelia yhteensä"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-ilmakaapelia-yhteensa}
    {:otsikko "Tienalituksia"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-tienalituksia}
    {:otsikko "Tienylityksiä"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-tienylityksia}
    {:otsikko "Silta-asennuksia"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-silta-asennuksia}
    {:otsikko (str "Kaapeliasennukset " (count (::tielupa/kaapeliasennukset valittu-tielupa)) "kpl")
     :tyyppi :komponentti
     :nimi ::tielupa/kaapeliasennukset
     :palstoja 3
     :komponentti (fn [{:keys [data]}]
                    [kaapelilupien-lomakegrid data])}))

(def nayta-johtoluvan-lomakekentat? (partial tiedot/nayta-kentat? johtoluvan-lomakekentat))

(defn mainosluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Mainoslupa"}
    {:otsikko "Mainostettava asia"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-mainostettava-asia}
    {:otsikko "Sijainnin kuvaus"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-sijainnin-kuvaus}
    {:otsikko "Korvaava päätös?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/mainoslupa-korvaava-paatos}
    {:otsikko "Tiedoksi ELY-keskukselle?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/mainoslupa-tiedoksi-elykeskukselle}
    {:otsikko "Asemakaava-alueella?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/mainoslupa-asemakaava-alueella}
    {:otsikko "Suoja-alueen leveys"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-suoja-alueen-leveys}
    {:otsikko "Lisätiedot"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-lisatiedot}
    {:otsikko (str "Mainokset " (count (::tielupa/mainokset valittu-tielupa)) "kpl")
     :nimi ::tielupa/mainokset
     :palstoja 3
     :tyyppi :komponentti
     :komponentti (fn [{:keys [data]}]
                    [mainosten-lomakegrid data])}))

(def nayta-mainosluvan-lomakekentat? (partial tiedot/nayta-kentat? mainosluvan-lomakekentat))

(defn opasteluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Opastelupa"}
    {:otsikko "Kohteen nimi"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-kohteen-nimi}
    {:otsikko "Palvelukohteen opastaulu?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/opastelupa-palvelukohteen-opastaulu}
    {:otsikko "Palvelukohteen osoiteviitta?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/opastelupa-palvelukohteen-osoiteviitta}
    {:otsikko "Osoiteviitta?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/opastelupa-osoiteviitta}
    {:otsikko "Ennakkomerkki"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-ennakkomerkki}
    {:otsikko "Opasteen teksti"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-opasteen-teksti}
    {:otsikko "Osoiteviitan tunnus"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-osoiteviitan-tunnus}
    {:otsikko "Lisätiedot"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-lisatiedot}
    {:otsikko "URL"
     :tyyppi :linkki
     :nimi ::tielupa/opastelupa-kohteen-url-osoite}
    {:otsikko "Jatkolupa?"
     :tyyppi :checkbox
     :fmt fmt/totuus
     :nimi ::tielupa/opastelupa-jatkolupa}
    {:otsikko "Alkuperäinen lupanumero"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-alkuperainen-lupanro}
    {:otsikko "Alkuperäisen luvan alkupäivämäärä"
     :tyyppi :pvm-aika
     :fmt pvm/pvm-aika-opt
     :nimi ::tielupa/opastelupa-alkuperaisen-luvan-alkupvm}
    {:otsikko "Alkuperäisen luvan loppupäivämäärä"
     :tyyppi :pvm-aika
     :fmt pvm/pvm-aika-opt
     :nimi ::tielupa/opastelupa-alkuperaisen-luvan-loppupvm}
    {:otsikko "Nykyinen opastus"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-nykyinen-opastus}
    {:otsikko (str "Opasteet " (count (::tielupa/opasteet valittu-tielupa)) "kpl")
     :tyyppi :komponentti
     :nimi ::tielupa/opasteet
     :palstoja 3
     :komponentti (fn [{:keys [data]}]
                    [opasteiden-lomakegrid data])}))

(def nayta-opasteluvan-lomakekentat? (partial tiedot/nayta-kentat? opasteluvan-lomakekentat))

(defn suoja-aluerakentamisluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Suoja-aluerakentamislupa"}
    {:otsikko "Rakennettava asia"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-rakennettava-asia}
    {:otsikko "Lisätiedot"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-lisatiedot}
    {:otsikko "Esitetty etäisyys tien keskilinjaan"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-esitetty-etaisyys-tien-keskilinjaan}
    {:otsikko "Vähimmäisetäisyys tien keskilinjasta"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-vahimmaisetaisyys-tien-keskilinjasta}
    {:otsikko "Leveys"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-suoja-alueen-leveys}
    {:otsikko "Sijoitus"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-sijoitus}
    {:otsikko "Kiinteistön rekisterinumero"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-kiinteisto-rn}))

(def nayta-suoja-aluerakentamisluvan-lomakekentat? (partial tiedot/nayta-kentat? suoja-aluerakentamisluvan-lomakekentat))

(defn myyntiluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Myyntilupa"}
    {:otsikko "Aihe"
     :tyyppi :string
     :nimi ::tielupa/myyntilupa-aihe}
    {:otsikko "Alueen nimi"
     :tyyppi :string
     :nimi ::tielupa/myyntilupa-alueen-nimi}
    {:otsikko "Aikaisempi myyntilupa"
     :tyyppi :string
     :nimi ::tielupa/myyntilupa-aikaisempi-myyntilupa}
    {:otsikko "Opastusmerkit"
     :tyyppi :string
     :nimi ::tielupa/myyntilupa-opastusmerkit}))

(def nayta-myyntiluvan-lomakekentat? (partial tiedot/nayta-kentat? myyntiluvan-lomakekentat))

(defn tielupalomake [e! {:keys [valittu-tielupa] :as app}]
  (do
   (js/console.log "tielupalomake ulkoinen tunniste" (pr-str (::tielupa/ulkoinen-tunniste valittu-tielupa)))
   [lomake/lomake
    {:luokka "ryhma-reuna"
     :otsikko (str (tielupa/tyyppi-fmt (::tielupa/tyyppi valittu-tielupa))
                " "
                (::tielupa/paatoksen-diaarinumero valittu-tielupa))
     :voi-muokata? false}
    [{:otsikko "Ulkoinen tunniste"
      :tyyppi :string
      :nimi ::tielupa/ulkoinen-tunniste}
     {:otsikko "Lupatyyppi"
      :tyyppi :string
      :nimi ::tielupa/tyyppi
      :fmt tielupa/tyyppi-fmt}
     {:otsikko "Diaarinumero"
      :tyyppi :string
      :nimi ::tielupa/paatoksen-diaarinumero}
     {:otsikko "Saapumispäivämäärä"
      :tyyppi :pvm-aika
      :fmt pvm/pvm-aika-opt
      :nimi ::tielupa/saapumispvm}
     {:otsikko "Myöntämispäivämäärä"
      :tyyppi :pvm-aika
      :fmt pvm/pvm-aika-opt
      :nimi ::tielupa/myontamispvm}
     {:otsikko "Voimassaolon alkupäivämäärä"
      :tyyppi :pvm-aika
      :fmt pvm/pvm-aika-opt
      :nimi ::tielupa/voimassaolon-alkupvm}
     {:otsikko "Voimassaolon loppupäivämäärä"
      :tyyppi :pvm-aika
      :fmt pvm/pvm-aika-opt
      :nimi ::tielupa/voimassaolon-loppupvm}
     {:otsikko "Otsikko"
      :tyyppi :string
      :nimi ::tielupa/otsikko}
     {:otsikko "Liite (linkki Tielupa-järjestelmän liitetilauspalveluun)"
      :tyyppi :linkki
      :nimi ::tielupa/liite-url}
     {:otsikko ""
      :tyyppi :linkki}
     ;{:otsikko "Katselmus (linkki Tielupa-järjestelmään)"
     ; :tyyppi :linkki
     ; :nimi ::tielupa/katselmus-url}
     {:otsikko "Urakka"
      :tyyppi :string
      :nimi ::tielupa/urakan-nimi}
     {:otsikko "Kunta"
      :tyyppi :string
      :nimi ::tielupa/kunta}
     {:otsikko "Lähiosoite"
      :tyyppi :string
      :nimi ::tielupa/kohde-lahiosoite}
     {:otsikko "Postinumero"
      :tyyppi :string
      :nimi ::tielupa/kohde-postinumero}
     {:otsikko "Postitoimipaikka"
      :tyyppi :string
      :nimi ::tielupa/kohde-postitoimipaikka}
     {:otsikko "Tien nimi"
      :tyyppi :string
      :nimi ::tielupa/tien-nimi}
     (when (nayta-sijaintigrid? valittu-tielupa)
       {:otsikko (str "Sijainnit " (count (::tielupa/sijainnit valittu-tielupa)) "kpl")
        :tyyppi :komponentti
        :nimi :sijainnit
        :palstoja 3
        :komponentti (fn [{data :data}]
                       [sijaintien-lomakegrid data])})
     (when (nayta-urakoitsijan-lomakekentat? valittu-tielupa)
       (urakoitsijan-lomakekentat valittu-tielupa))

     (when (nayta-liikenneohjaajan-lomakekentat? valittu-tielupa)
       (liikenneohjaajan-lomakekentat valittu-tielupa))

     (when (nayta-tienpitoviranomaisen-lomakekentat? valittu-tielupa)
       (tienpitoviranomaisen-lomakekentat valittu-tielupa))

     (when (nayta-valmistumisilmoituksen-lomakekentat? valittu-tielupa)
       (valmistumisilmoituksen-lomakekentat valittu-tielupa))

     (when (nayta-johtoluvan-lomakekentat? valittu-tielupa)
       (johtoluvan-lomakekentat valittu-tielupa))

     (when (nayta-liittymaluvan-lomakekentat? valittu-tielupa)
       (liittymaluvan-lomakekentat valittu-tielupa))

     (when (nayta-liittymaluvan-liittymaohjeen-lomakekentat? valittu-tielupa)
       (liittymaluvan-liittymaohjeen-lomakekentat valittu-tielupa))

     (when (nayta-mainosluvan-lomakekentat? valittu-tielupa)
       (mainosluvan-lomakekentat valittu-tielupa))

     (when (nayta-opasteluvan-lomakekentat? valittu-tielupa)
       (opasteluvan-lomakekentat valittu-tielupa))

     (when (nayta-suoja-aluerakentamisluvan-lomakekentat? valittu-tielupa)
       (suoja-aluerakentamisluvan-lomakekentat valittu-tielupa))

     (when (nayta-myyntiluvan-lomakekentat? valittu-tielupa)
       (myyntiluvan-lomakekentat valittu-tielupa))

     (when (nayta-liikennemerkkijarjestelyn-lomakekentat? valittu-tielupa)
       (liikennemerkkijarjestelyn-lomakekentat valittu-tielupa))

     (when (nayta-tyoluvan-lomakekentat? valittu-tielupa)
       (tyoluvan-lomakekentat valittu-tielupa))

     (when (nayta-vesihuoltoluvan-lomakekentat? valittu-tielupa)
       (vesihuoltoluvan-lomakekentat valittu-tielupa))

     (when (nayta-hakijan-lomakekentat? valittu-tielupa)
       (hakijan-lomakekentat valittu-tielupa))]
    valittu-tielupa]))

;; tielupalomake-komponenttia käytetään tilanenkuvan modaalissa, missä ei haluta näyttää takaisin-nappia
(defn tielupalomake* [e! app]
  [:div
   [napit/takaisin "Takaisin lupataulukkoon" #(e! (tiedot/->ValitseTielupa nil))]
   (when (and (:valittu-tielupa app) (false? (:tielupien-haku-kaynnissa? app)))
     [tielupalomake e! app])])