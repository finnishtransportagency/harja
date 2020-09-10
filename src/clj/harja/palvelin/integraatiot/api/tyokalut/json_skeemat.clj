(ns harja.palvelin.integraatiot.api.tyokalut.json-skeemat
  (:require [harja.tyokalut.json-validointi :refer [tee-validaattori]]))

(def +onnistunut-kirjaus+ "api/schemas/kirjaus-response.schema.json")
(def onnistunut-kirjaus (tee-validaattori "api/schemas/kirjaus-response.schema.json"))
(def +virhevastaus+ "api/schemas/virhe-response.schema.json")
(def virhevastaus (tee-validaattori "api/schemas/virhe-response.schema.json"))
(def +kirjausvastaus+ "api/schemas/kirjaus-response.schema.json")
(def kirjausvastaus (tee-validaattori "api/schemas/kirjaus-response.schema.json"))

(def +urakan-haku-vastaus+ "api/schemas/urakan-haku-response.schema.json")
(def urakan-haku-vastaus (tee-validaattori "api/schemas/urakan-haku-response.schema.json"))
(def +urakoiden-haku-vastaus+ "api/schemas/urakoiden-haku-response.schema.json")
(def urakoiden-haku-vastaus (tee-validaattori "api/schemas/urakoiden-haku-response.schema.json"))

(def +laatupoikkeaman-kirjaus+ "api/schemas/laatupoikkeaman-kirjaus-request.schema.json")
(def laatupoikkeaman-kirjaus (tee-validaattori "api/schemas/laatupoikkeaman-kirjaus-request.schema.json"))

(def +ilmoitustoimenpiteen-kirjaaminen+ "api/schemas/ilmoitustoimenpiteen-kirjaaminen-request.schema.json")
(def ilmoitustoimenpiteen-kirjaaminen (tee-validaattori "api/schemas/ilmoitustoimenpiteen-kirjaaminen-request.schema.json"))
(def +ilmoitusten-haku+ "api/schemas/ilmoitusten-haku-response.schema.json")
(def ilmoitusten-haku (tee-validaattori "api/schemas/ilmoitusten-haku-response.schema.json"))
(def +tietyoilmoituksen-kirjaus+ "api/schemas/tietyoilmoituksen-kirjaus-request.schema.json")
(def tietyoilmoituksen-kirjaus (tee-validaattori "api/schemas/tietyoilmoituksen-kirjaus-request.schema.json"))

(def +pistetoteuman-kirjaus+ "api/schemas/pistetoteuman-kirjaus-request.schema.json")
(def pistetoteuman-kirjaus (tee-validaattori "api/schemas/pistetoteuman-kirjaus-request.schema.json"))
(def +pistetoteuman-poisto+ "api/schemas/toteuman-poisto-request.schema.json")
(def pistetoteuman-poisto (tee-validaattori "api/schemas/toteuman-poisto-request.schema.json"))
(def +reittitoteuman-kirjaus+ "api/schemas/reittitoteuman-kirjaus-request.schema.json")
(def reittitoteuman-kirjaus (tee-validaattori "api/schemas/reittitoteuman-kirjaus-request.schema.json"))
(def +reittitoteuman-poisto+ "api/schemas/toteuman-poisto-request.schema.json")
(def reittitoteuman-poisto (tee-validaattori "api/schemas/toteuman-poisto-request.schema.json"))

(def +turvallisuuspoikkeamien-kirjaus+ "api/schemas/turvallisuuspoikkeamien-kirjaus-request.schema.json")
(def turvallisuuspoikkeamien-kirjaus (tee-validaattori "api/schemas/turvallisuuspoikkeamien-kirjaus-request.schema.json"))

(def +tielupien-haku+ "api/schemas/tielupien-haku-request.schema.json")
(def tielupien-haku (tee-validaattori "api/schemas/tielupien-haku-request.schema.json"))
(def +tielupien-haku-vastaus+ "api/schemas/tielupien-haku-response.schema.json")
(def tielupien-haku-vastaus (tee-validaattori "api/schemas/tielupien-haku-response.schema.json"))

(def +tietolajien-haku+ "api/schemas/tietolajien-haku-response.schema.json")
(def tietolajien-haku (tee-validaattori "api/schemas/tietolajien-haku-response.schema.json"))

(def +varusteiden-haku-vastaus+ "api/schemas/varusteiden-haku-response.schema.json")
(def varusteiden-haku-vastaus (tee-validaattori "api/schemas/varusteiden-haku-response.schema.json"))
(def +varusteen-lisays+ "api/schemas/varusteen-lisays-request.schema.json")
(def varusteen-lisays (tee-validaattori "api/schemas/varusteen-lisays-request.schema.json"))
(def +varusteen-paivitys+ "api/schemas/varusteen-paivitys-request.schema.json")
(def varusteen-paivitys (tee-validaattori "api/schemas/varusteen-paivitys-request.schema.json"))
(def +varusteen-poisto+ "api/schemas/varusteen-poisto-request.schema.json")
(def varusteen-poisto (tee-validaattori "api/schemas/varusteen-poisto-request.schema.json"))
(def +varustetoteuman-kirjaus+ "api/schemas/varustetoteuman-kirjaus-request.schema.json")
(def varustetoteuman-kirjaus (tee-validaattori "api/schemas/varustetoteuman-kirjaus-request.schema.json"))
(def +varustetoteuman-poisto+ "api/schemas/toteuman-poisto-request.schema.json")
(def varustetoteuman-poisto (tee-validaattori "api/schemas/toteuman-poisto-request.schema.json"))

(def +siltatarkastuksen-kirjaus+ "api/schemas/siltatarkastuksen-kirjaus-request.schema.json")
(def siltatarkastuksen-kirjaus (tee-validaattori "api/schemas/siltatarkastuksen-kirjaus-request.schema.json"))
(def +siltatarkastuksen-poisto+ "api/schemas/tarkastuksen-poisto-request.schema.json")
(def siltatarkastuksen-poisto (tee-validaattori "api/schemas/tarkastuksen-poisto-request.schema.json"))
(def +tiestotarkastuksen-kirjaus+ "api/schemas/tiestotarkastuksen-kirjaus-request.schema.json")
(def tiestotarkastuksen-kirjaus (tee-validaattori "api/schemas/tiestotarkastuksen-kirjaus-request.schema.json"))
(def +tiestotarkastuksen-poisto+ "api/schemas/tarkastuksen-poisto-request.schema.json")
(def tiestotarkastuksen-poisto (tee-validaattori "api/schemas/tarkastuksen-poisto-request.schema.json"))
(def +soratietarkastuksen-kirjaus+ "api/schemas/soratietarkastuksen-kirjaus-request.schema.json")
(def soratietarkastuksen-kirjaus (tee-validaattori "api/schemas/soratietarkastuksen-kirjaus-request.schema.json"))
(def +soratietarkastuksen-poisto+ "api/schemas/tarkastuksen-poisto-request.schema.json")
(def soratietarkastuksen-poisto (tee-validaattori "api/schemas/tarkastuksen-poisto-request.schema.json"))
(def +talvihoitotarkastuksen-kirjaus+ "api/schemas/talvihoitotarkastuksen-kirjaus-request.schema.json")
(def talvihoitotarkastuksen-kirjaus (tee-validaattori "api/schemas/talvihoitotarkastuksen-kirjaus-request.schema.json"))
(def +talvihoitotarkastuksen-poisto+ "api/schemas/tarkastuksen-poisto-request.schema.json")
(def talvihoitotarkastuksen-poisto (tee-validaattori "api/schemas/tarkastuksen-poisto-request.schema.json"))

(def +paivystajatietojen-kirjaus+ "api/schemas/paivystajatietojen-kirjaus-request.schema.json")
(def paivystajatietojen-kirjaus (tee-validaattori "api/schemas/paivystajatietojen-kirjaus-request.schema.json"))
(def +paivystajatietojen-haku-vastaus+ "api/schemas/paivystajatietojen-haku-response.schema.json")
(def paivystajatietojen-haku-vastaus (tee-validaattori "api/schemas/paivystajatietojen-haku-response.schema.json"))
(def +paivystajatietojen-poisto+ "api/schemas/paivystajatietojen-poisto-request.schema.json")
(def paivystyksen-poisto (tee-validaattori "api/schemas/paivystyksen-poisto-request.schema.json"))

(def +tyokoneenseuranta-kirjaus+ "api/schemas/tyokoneenseurannan-kirjaus-request.schema.json")
(def tyokoneenseuranta-kirjaus (tee-validaattori "api/schemas/tyokoneenseurannan-kirjaus-request.schema.json"))
(def +tyokoneenseuranta-viivakirjaus+ "api/schemas/tyokoneenseurannan-viivakirjaus-request.schema.json")
(def tyokoneenseuranta-viivakirjaus (tee-validaattori "api/schemas/tyokoneenseurannan-viivakirjaus-request.schema.json"))

(def +urakan-yllapitokohteiden-haku-vastaus+ "api/schemas/urakan-yllapitokohteet-response.schema.json")
(def urakan-yllapitokohteiden-haku-vastaus (tee-validaattori +urakan-yllapitokohteiden-haku-vastaus+))

(def +paallystysilmoituksen-kirjaus+ "api/schemas/paallystysilmoituksen-kirjaus-request.schema.json")
(def paallystysilmoituksen-kirjaus (tee-validaattori +paallystysilmoituksen-kirjaus+))

(def +paallystyksen-aikataulun-kirjaus+ "api/schemas/paallystyksen-aikataulun-kirjaus-request.schema.json")
(def paallystyksen-aikataulun-kirjaus (tee-validaattori +paallystyksen-aikataulun-kirjaus+))
(def +tiemerkinnan-aikataulun-kirjaus+ "api/schemas/tiemerkinnan-aikataulun-kirjaus-request.schema.json")
(def tiemerkinnan-aikataulun-kirjaus (tee-validaattori +tiemerkinnan-aikataulun-kirjaus+))

(def +tietyomaan-kirjaus+ "api/schemas/tietyomaan-kirjaus-request.schema.json")
(def tietyomaan-kirjaus (tee-validaattori +tietyomaan-kirjaus+))
(def +tietyomaan-poisto+ "api/schemas/tietyomaan-poisto-request.schema.json")
(def tietyomaan-poisto (tee-validaattori +tietyomaan-poisto+))

(def +urakan-yhteystietojen-haku-vastaus+ "api/schemas/urakan-yhteystietojen-haku-response.schema.json")
(def urakan-yhteystietojen-haku-vastaus (tee-validaattori +urakan-yhteystietojen-haku-vastaus+))

(def +urakan-yllapitokohteen-paivitys-request+ "api/schemas/urakan-yllapitokohteen-paivitys-request.schema.json")
(def urakan-yllapitokohteen-paivitys-request (tee-validaattori +urakan-yllapitokohteen-paivitys-request+))

(def +urakan-yllapitokohteen-maaramuutosten-kirjaus-request+ "api/schemas/urakan-yllapitokohteen-maaramuutosten-kirjaus-request.schema.json")
(def urakan-yllapitokohteen-maaramuutosten-kirjaus-request (tee-validaattori +urakan-yllapitokohteen-maaramuutosten-kirjaus-request+))

(def +urakan-yllapitokohteen-tarkastuksen-kirjaus-request+ "api/schemas/yllapitokohteen-tarkastuksen-kirjaus-request.schema.json")
(def urakan-yllapitokohteen-tarkastuksen-kirjaus-request (tee-validaattori +urakan-yllapitokohteen-tarkastuksen-kirjaus-request+))

(def +urakan-yllapitokohteen-tarkastuksen-poisto-request+ "api/schemas/tarkastuksen-poisto-request.schema.json")
(def urakan-yllapitokohteen-tarkastuksen-poisto-request (tee-validaattori +urakan-yllapitokohteen-tarkastuksen-poisto-request+))

(def +urakan-tiemerkintatoteuman-kirjaus-request+ "api/schemas/urakan-tiemerkintatoteuman-kirjaus-request.schema.json")
(def urakan-tiemerkintatoteuman-kirjaus-request (tee-validaattori "api/schemas/urakan-tiemerkintatoteuman-kirjaus-request.schema.json"))

(def +urakan-yllapitokohteen-tiemerkintatoteuman-kirjaus-request+ "api/schemas/urakan-yllapitokohteen-tiemerkintatoteuman-kirjaus-request.schema.json")
(def urakan-yllapitokohteen-tiemerkintatoteuman-kirjaus-request (tee-validaattori "api/schemas/urakan-yllapitokohteen-tiemerkintatoteuman-kirjaus-request.schema.json"))

(def +urakan-tyotuntien-kirjaus-request+ "api/schemas/urakan-tyotuntien-kirjaus-request.schema.json")
(def urakan-tyotuntien-kirjaus-request (tee-validaattori "api/schemas/urakan-tyotuntien-kirjaus-request.schema.json"))

(def +tieluvan-kirjaus-request+ "api/schemas/tieluvan-kirjaus-request.schema.json")
(def tieluvan-kirjaus-request (tee-validaattori "api/schemas/tieluvan-kirjaus-request.schema.json"))

(def +paikkausten-kirjaus-request+ "api/schemas/paikkausten-kirjaus-request.schema.json")
(def paikkausten-kirjaus-request (tee-validaattori +paikkausten-kirjaus-request+))

(def +paikkauskustannusten-kirjaus-request+ "api/schemas/paikkauskustannusten-kirjaus-request.schema.json")
(def paikkauskustannusten-kirjaus-request (tee-validaattori +paikkauskustannusten-kirjaus-request+))

(def +paikkausten-poisto-request+ "api/schemas/paikkausten-poisto-request.schema.json")
(def paikkausten-poisto-request (tee-validaattori +paikkausten-poisto-request+))
