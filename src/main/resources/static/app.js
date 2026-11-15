// Front end do ERP Notas: JavaScript puro, sem build. Consome a API REST deste mesmo servidor.
// O papel lido do token só esconde botões; quem garante as permissões é o back end.

const $ = (seletor) => document.querySelector(seletor);
let token = sessionStorage.getItem('token');

// ---------- utilitários ----------

/** Cria elementos com textContent (nunca innerHTML), para dados da API não virarem HTML/XSS. */
function el(tag, attrs = {}, ...filhos) {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
        if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
        else if (v !== false && v != null) e.setAttribute(k, v === true ? '' : v);
    }
    e.append(...filhos.flat().filter(f => f != null && f !== false));
    return e;
}

function aviso(msg, erro = false) {
    const a = $('#aviso');
    a.textContent = msg;
    a.className = erro ? 'erro' : '';
    a.hidden = !msg;
}

const moeda = (v) => v == null ? '' : Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const dataHora = (v) => v ? new Date(v).toLocaleString('pt-BR') : '';
const ler = (obj, caminho) => caminho.split('.').reduce((o, k) => o?.[k], obj);

// ---------- máscaras e validação de campos ----------

/**
 * Formata o valor segundo um molde: '#' aceita só dígito, 'A' aceita dígito ou letra (CNPJ alfanumérico);
 * qualquer outro caractere do molde é separador. Caracteres inválidos são descartados.
 */
function aplicarMolde(valor, molde) {
    const chars = String(valor ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '');
    let saida = '';
    let i = 0;
    for (const m of molde) {
        if (m !== '#' && m !== 'A') {
            if (i < chars.length) saida += m;
            continue;
        }
        while (i < chars.length && !(m === '#' ? /\d/ : /[0-9A-Z]/).test(chars[i])) i++;
        if (i >= chars.length) break;
        saida += chars[i++];
    }
    return saida.replace(/[^0-9A-Z]+$/, ''); // sem separador sobrando no fim
}

const MOLDE_CPF = '###.###.###-##';
const MOLDE_CNPJ = 'AA.AAA.AAA/AAAA-##'; // CNPJ alfanumérico (desde jul/2026): letras nas 12 primeiras posições

/** formatar: aplica a máscara; max: tamanho máximo já formatado; valido: regra extra; limpar: envia à API sem máscara. */
const MASCARAS = {
    cnpj: { formatar: v => aplicarMolde(v, MOLDE_CNPJ), max: 18, valido: cnpjValido, limpar: true, msg: 'CNPJ inválido' },
    documento: {
        // Até 11 dígitos é CPF; com letra ou mais de 11 caracteres passa a ser CNPJ.
        formatar: v => {
            const limpo = String(v ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '');
            return aplicarMolde(limpo, /[A-Z]/.test(limpo) || limpo.length > 11 ? MOLDE_CNPJ : MOLDE_CPF);
        },
        max: 18, valido: v => cpfValido(v) || cnpjValido(v), limpar: true, msg: 'CPF ou CNPJ inválido',
    },
    telefone: {
        formatar: v => {
            const d = String(v ?? '').replace(/\D/g, '');
            return aplicarMolde(d, d.length > 10 ? '(##) #####-####' : '(##) ####-####');
        },
        max: 15, valido: v => /^\d{10,11}$/.test(v), msg: 'Telefone deve ter DDD + 8 ou 9 dígitos',
    },
    cep: { formatar: v => aplicarMolde(v, '#####-###'), max: 9, valido: v => /^\d{8}$/.test(v), limpar: true, msg: 'CEP deve ter 8 dígitos' },
};

const somenteAlfanum = v => String(v).toUpperCase().replace(/[^0-9A-Z]/g, '');

/** Aplica a máscara e marca o campo como inválido (o navegador então bloqueia o envio e mostra a mensagem). */
function mascarar(input) {
    const m = MASCARAS[input.dataset.mascara];
    if (!m) return;
    input.value = m.formatar(input.value);
    const limpo = somenteAlfanum(input.value);
    input.setCustomValidity(limpo && !m.valido(limpo) ? m.msg : '');
}

// shortcut: ao editar no meio do texto o cursor vai para o fim; resolver guardando selectionStart se incomodar.
document.addEventListener('input', ev => mascarar(ev.target));

/** Dígitos verificadores módulo 11, igual à classe Documentos do back end. */
function digitoMod11(valores, pesos) {
    const soma = valores.reduce((s, v, i) => s + v * pesos[i], 0);
    const resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
}

function cpfValido(v) {
    if (!/^\d{11}$/.test(v) || /^(\d)\1{10}$/.test(v)) return false;
    const n = [...v].map(Number);
    return digitoMod11(n.slice(0, 9), [10, 9, 8, 7, 6, 5, 4, 3, 2]) === n[9]
        && digitoMod11(n.slice(0, 10), [11, 10, 9, 8, 7, 6, 5, 4, 3, 2]) === n[10];
}

function cnpjValido(v) {
    if (!/^[0-9A-Z]{12}\d{2}$/.test(v) || /^(.)\1{13}$/.test(v)) return false;
    const n = [...v].map(c => c.charCodeAt(0) - 48); // valor do caractere = ASCII - 48
    const pesos = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    return digitoMod11(n.slice(0, 12), pesos.slice(1)) === n[12]
        && digitoMod11(n.slice(0, 13), pesos) === n[13];
}


function claims() {
    try {
        return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    } catch {
        return null;
    }
}
const isAdmin = () => claims()?.role === 'ADMIN';
const isGestor = () => ['ADMIN', 'GERENTE'].includes(claims()?.role); // cadastram empresas e usuários
const temEmpresa = () => claims()?.empresaId != null;
const valor = (v) => typeof v === 'function' ? v() : v;

/** Empresas que o usuário pode acessar; guarda os nomes para mostrar nas outras telas. */
const nomesEmpresas = new Map();
async function carregarEmpresas() {
    const empresas = await api('GET', '/api/empresas');
    empresas.forEach(e => nomesEmpresas.set(e.id, e.razaoSocial));
    return empresas;
}

async function api(metodo, url, corpo) {
    const resp = await fetch(url, {
        method: metodo,
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
        body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
    if (resp.status === 401 && token) {
        sair();
        throw new Error('Sessão expirada. Entre novamente.');
    }
    const texto = await resp.text();
    const dados = texto ? JSON.parse(texto) : null;
    if (!resp.ok) {
        const campos = dados?.erros ? ': ' + Object.entries(dados.erros).map(([c, m]) => `${c} ${m}`).join('; ') : '';
        throw new Error((dados?.detail || `Erro ${resp.status}`) + campos);
    }
    return dados;
}

/** Executa uma ação mostrando o erro da API no aviso, em vez de deixar a promise falhar em silêncio. */
async function tentar(acao, sucesso) {
    try {
        await acao();
        if (sucesso) aviso(sucesso);
    } catch (e) {
        aviso(e.message, true);
    }
}

// ---------- telas: configuração de cada recurso ----------

// max = tamanho da coluna no banco; mascara = chave de MASCARAS.
const ENDERECO = [
    { n: 'logradouro', l: 'Logradouro', max: 150 }, { n: 'numero', l: 'Número', max: 20 },
    { n: 'complemento', l: 'Complemento', max: 100 }, { n: 'bairro', l: 'Bairro', max: 100 },
    { n: 'cep', l: 'CEP', mascara: 'cep' },
    // o código IBGE fica salvo; cidade e UF vêm da tabela do IBGE (o back end também as grava a partir do código)
    { n: 'codigoMunicipio', l: 'Município', tipo: 'municipio' },
    { n: 'cidade', l: 'Cidade (automático)', somenteLeitura: true }, { n: 'uf', l: 'UF (automático)', somenteLeitura: true },
].map(c => ({ ...c, n: 'endereco.' + c.n }));

const opcoes = (url, rotulo, filtro = () => true) => async () =>
    (await api('GET', url)).filter(filtro).map(o => [o.id, rotulo(o)]);

const statusNota = (s) => el('span', { class: 'status ' + s }, s);

// ---------- itens de serviço (lista fixa do back end) ----------

const listasServico = {}; // url -> itens (as listas são fixas, basta buscar uma vez)
const carregarItensServico = async (url) => listasServico[url] ??= await api('GET', url);
const simplificar = (s) => String(s ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();

/** Mesma regra da busca do back end: só dígitos e pontos = começo do código; senão, parte da descrição sem acentos. */
function itemCombina(item, termo) {
    const t = simplificar(termo);
    if (!t) return true;
    if (/^[\d.]+$/.test(t)) return item.codigo.replace(/\./g, '').startsWith(t.replace(/\./g, ''));
    return simplificar(item.descricao).includes(t);
}

const rotuloItem = (i) => `${i.codigo} - ${i.descricao}`;
const URL_MUNICIPIOS = '/api/itens-servico/municipios';

/** Campo de busca que filtra um select com os itens da lista c.url; o valor enviado é só o código. */
async function campoItemServico(c, valor) {
    const itens = await carregarItensServico(c.url);
    const select = el('select', { name: c.n, required: c.req, 'aria-label': c.l });
    const busca = el('input', { type: 'search', autocomplete: 'off',
        placeholder: c.placeholder ?? 'Buscar por código (ex.: 01.01 ou 0101) ou descrição' });
    const filtrar = () => {
        const atual = select.value || valor;
        const achados = itens.filter(i => itemCombina(i, busca.value));
        select.replaceChildren(el('option', { value: '' }, `Selecione... (${achados.length} encontrados)`),
            ...itens.filter(i => i.codigo === atual && !achados.includes(i))
                .concat(achados)
                .map(i => el('option', { value: i.codigo, selected: i.codigo === atual }, (c.rotulo ?? rotuloItem)(i))));
    };
    busca.addEventListener('input', filtrar);
    select.addEventListener('change', () => { // sugere a descrição do item se o campo estiver vazio
        const descricao = select.form?.elements.descricao;
        const item = itens.find(i => i.codigo === select.value);
        if (c.n === 'codigoServico' && descricao && !descricao.value && item) descricao.value = item.descricao.slice(0, 500);
        // item municipal escolhido: sugere o cTribNac com o mesmo item e subitem (ex.: 01.01.02 -> 0101xx)
        const nacional = select.form?.elements.codigoTributacaoNacional;
        if (c.n === 'codigoServico' && nacional && !nacional.value) {
            const prefixo = select.value.replace(/\./g, '').slice(0, 4);
            const sugerido = [...nacional.options].find(o => o.value && o.value.startsWith(prefixo));
            if (sugerido) nacional.value = sugerido.value;
        }
        // município escolhido: preenche cidade e UF do mesmo grupo de endereço (ex.: endereco.cidade)
        const prefixo = c.n.replace(/codigoMunicipio$/, '');
        const cidade = select.form?.elements[prefixo + 'cidade'];
        const uf = select.form?.elements[prefixo + 'uf'];
        if (c.url === URL_MUNICIPIOS && cidade && uf) {
            cidade.value = item?.descricao ?? '';
            uf.value = item?.grupo ?? '';
        }
        // cClassTrib escolhido: o CST é o do grupo dele
        const cst = select.form?.elements.cstIbsCbs;
        if (c.n === 'classificacaoTributariaIbsCbs' && cst && item) {
            cst.value = item.grupo;
            cst.dispatchEvent(new Event('change'));
        }
    });
    filtrar();
    return el('label', { class: 'inteiro' }, c.l, busca, select);
}

const TELAS = {
    notas: {
        titulo: 'Notas fiscais', url: '/api/notas-fiscais', editar: false, excluir: null,
        colunas: [['numero', 'Nº'], ['clienteNome', 'Cliente'], ['descricao', 'Descrição'],
            ['valorLiquido', 'Líquido', moeda], ['status', 'Status', statusNota], ['numeroNfse', 'NFS-e'],
            ['dataEmissao', 'Emissão', dataHora]],
        campos: [
            { n: 'clienteId', l: 'Cliente', tipo: 'select', req: true, opcoes: opcoes('/api/clientes', c => c.nome) },
            { n: 'servicoId', l: 'Serviço (ativos)', tipo: 'select', req: true,
                opcoes: opcoes('/api/servicos', s => `${s.codigoServico} - ${s.descricao} (${moeda(s.valorPadrao)})`, s => s.ativo) },
            { n: 'descricao', l: 'Descrição (vazio = do serviço; [REJEITAR] força rejeição)', max: 1000 },
            { n: 'valorServico', l: 'Valor do serviço (vazio = padrão)', tipo: 'number' },
            { n: 'valorDesconto', l: 'Desconto incondicionado', tipo: 'number' },
            // dados da DPS (leiaute v1.01)
            { n: 'dataCompetencia', l: 'Data de competência (vazio = hoje)', tipo: 'date' },
            { n: 'municipioPrestacao', l: 'Município da prestação (vazio = o da empresa)', tipo: 'municipio' },
            { n: 'tributacaoIssqn', l: 'Tributação do ISSQN', tipo: 'select', req: true, padrao: 1, opcoes: async () =>
                [[1, '1 - Operação tributável'], [2, '2 - Imunidade'], [4, '4 - Não incidência']] },
            { n: 'tipoImunidade', l: 'Tipo de imunidade (só imunidade)', tipo: 'select', opcoes: async () => [
                [0, '0 - Não informado na nota de origem'], [1, '1 - Patrimônio, renda ou serviços (CF art. 150, VI, a)'],
                [2, '2 - Templos de qualquer culto (b)'], [3, '3 - Partidos, sindicatos, educação e assistência (c)'],
                [4, '4 - Livros, jornais e periódicos (d)'], [5, '5 - Fonogramas e videofonogramas (e)']] },
            { n: 'retencaoIssqn', l: 'Retenção do ISSQN', tipo: 'select', req: true, padrao: 1, opcoes: async () =>
                [[1, '1 - Não retido'], [2, '2 - Retido pelo tomador']] },
            { n: 'obraCodigo', l: 'Código da obra CNO/CEI (serviços de obra)', max: 30 },
            { n: 'eventoNome', l: 'Nome do evento (serviços de evento)', max: 255 },
            { n: 'eventoInicio', l: 'Início do evento', tipo: 'date' },
            { n: 'eventoFim', l: 'Fim do evento', tipo: 'date' },
            { n: 'eventoCodigo', l: 'Código do evento (idAtvEvt)', max: 30 },
        ],
        empresa: true,
        acoes: (nota, recarregar) => [
            ['PENDENTE', 'REJEITADA'].includes(nota.status) &&
                el('button', { onclick: () => tentar(async () => {
                    const r = await api('POST', `/api/notas-fiscais/${nota.id}/emitir`);
                    await recarregar();
                    aviso(`Nota ${r.numero}: ${r.status}. ${r.mensagemRetorno}`, r.status === 'REJEITADA');
                }) }, 'Emitir'),
            nota.status === 'AUTORIZADA' && isGestor() &&
                el('button', { class: 'perigo', onclick: () => confirm(`Cancelar a nota ${nota.numero}?`) && tentar(async () => {
                    await api('POST', `/api/notas-fiscais/${nota.id}/cancelar`);
                    await recarregar();
                }, 'Nota cancelada (simulação).') }, 'Cancelar'),
            el('button', { class: 'secundario', onclick: () => tentar(() => detalhesNota(nota.id)) }, 'Detalhes'),
        ],
    },
    clientes: {
        titulo: 'Clientes', url: '/api/clientes', excluir: 'Excluir', empresa: true,
        colunas: [['nome', 'Nome'], ['documento', 'CPF/CNPJ', MASCARAS.documento.formatar], ['email', 'E-mail'], ['telefone', 'Telefone']],
        campos: [{ n: 'nome', l: 'Nome', req: true, max: 150 }, { n: 'documento', l: 'CPF/CNPJ', mascara: 'documento' },
            { n: 'email', l: 'E-mail', tipo: 'email', max: 150 }, { n: 'telefone', l: 'Telefone', mascara: 'telefone' }, ...ENDERECO],
    },
    servicos: {
        titulo: 'Serviços', url: '/api/servicos', excluir: 'Desativar', empresa: true,
        colunas: [['descricao', 'Descrição'], ['codigoServico', 'Código'], ['codigoTributacaoNacional', 'cTribNac'],
            ['valorPadrao', 'Valor', moeda], ['ativo', 'Ativo', v => v ? 'Sim' : 'Não']],
        campos: [{ n: 'codigoServico', l: 'Item de serviço (lista municipal, LC 116)', tipo: 'itemServico', url: '/api/itens-servico', req: true },
            { n: 'codigoTributacaoNacional', l: 'Código de tributação nacional (cTribNac, Lista Nacional de Serviços)',
                tipo: 'itemServico', url: '/api/itens-servico/nacionais', req: true },
            { n: 'descricao', l: 'Descrição', req: true, max: 500 },
            { n: 'valorPadrao', l: 'Valor padrão', tipo: 'number', req: true },
            { n: 'aliquotaIss', l: 'Alíquota do ISS % (pAliq, opcional, até 5)', tipo: 'number' },
            { n: 'codigoNbs', l: 'Código NBS 2.0 (cNBS, opcional)', tipo: 'itemServico', url: '/api/itens-servico/nbs' },
            { n: 'indicadorOperacao', l: 'IBS/CBS: indicador da operação (cIndOp)', tipo: 'itemServico',
                url: '/api/itens-servico/indicadores-operacao' },
            { n: 'classificacaoTributariaIbsCbs', l: 'IBS/CBS: classificação tributária (cClassTrib, válidas para NFS-e)',
                tipo: 'itemServico', url: '/api/itens-servico/cclasstrib' },
            { n: 'cstIbsCbs', l: 'IBS/CBS: CST (preenchido pelo cClassTrib)', tipo: 'itemServico', url: '/api/itens-servico/cst' },
            { n: 'ativo', l: 'Ativo', tipo: 'checkbox' }],
    },
    empresas: {
        titulo: 'Empresas', url: '/api/empresas', criar: isGestor, editar: isGestor, excluir: () => isGestor() && 'Desativar',
        colunas: [['razaoSocial', 'Razão social'], ['nomeFantasia', 'Nome fantasia'], ['cnpj', 'CNPJ', MASCARAS.cnpj.formatar],
            ['inscricaoMunicipal', 'Insc. municipal'], ['ativo', 'Ativa', v => v ? 'Sim' : 'Não']],
        campos: [{ n: 'razaoSocial', l: 'Razão social', req: true, max: 150 }, { n: 'nomeFantasia', l: 'Nome fantasia', max: 150 },
            { n: 'cnpj', l: 'CNPJ', req: true, mascara: 'cnpj' }, { n: 'inscricaoMunicipal', l: 'Inscrição municipal', max: 20 },
            { n: 'email', l: 'E-mail', tipo: 'email', max: 150 }, { n: 'telefone', l: 'Telefone', mascara: 'telefone' },
            // regime tributário do prestador (prest/regTrib da DPS)
            { n: 'opcaoSimplesNacional', l: 'Simples Nacional (opSimpNac)', tipo: 'select', req: true, padrao: 1, opcoes: async () =>
                [[1, '1 - Não optante'], [2, '2 - MEI'], [3, '3 - ME/EPP']] },
            { n: 'regimeApuracaoSimplesNacional', l: 'Regime de apuração do SN (só ME/EPP)', tipo: 'select', opcoes: async () => [
                [1, '1 - Tributos federais e municipal pelo SN'], [2, '2 - Federais pelo SN e ISSQN pela NFS-e'],
                [3, '3 - Federais e municipal pela NFS-e']] },
            { n: 'regimeEspecialTributacao', l: 'Regime especial de tributação (regEspTrib)', tipo: 'select', req: true, padrao: 0,
                opcoes: async () => [[0, '0 - Nenhum'], [1, '1 - Ato cooperado'], [2, '2 - Estimativa'], [3, '3 - Microempresa municipal'],
                    [4, '4 - Notário ou registrador'], [5, '5 - Profissional autônomo'], [6, '6 - Sociedade de profissionais'], [9, '9 - Outros']] },
            { n: 'percentualTributosSimplesNacional', l: '% aproximado de tributos do SN (pTotTribSN, só ME/EPP)', tipo: 'number' },
            ...ENDERECO],
        acoes: (empresa) => [
            empresa.id === claims()?.empresaId ? el('strong', {}, 'Em uso ') :
                empresa.ativo && el('button', { onclick: () => tentar(async () =>
                    entrar((await api('POST', `/api/empresas/${empresa.id}/acessar`)).token)) }, 'Acessar'),
        ],
    },
    usuarios: {
        titulo: 'Usuários', url: '/api/usuarios', excluir: 'Desativar', gestor: true, antes: carregarEmpresas,
        colunas: [['nome', 'Nome'], ['email', 'E-mail'], ['role', 'Tipo'], ['ativo', 'Ativo', v => v ? 'Sim' : 'Não'],
            ['empresaIds', 'Empresas liberadas', ids => ids.map(id => nomesEmpresas.get(id) ?? `#${id}`).join(', ')]],
        campos: [{ n: 'nome', l: 'Nome', req: true, max: 150 }, { n: 'email', l: 'E-mail', tipo: 'email', req: true, max: 150 },
            { n: 'senha', l: 'Senha (mín. 8; na edição, vazio = mantém)', tipo: 'password', min: 8, max: 72 },
            { n: 'role', l: 'Tipo', tipo: 'select', req: true, opcoes: async (u) => u?.role === 'ADMIN' ? [['ADMIN', 'ADMIN']]
                    : [['NORMAL', 'Normal'], ...(isAdmin() ? [['GERENTE', 'Gerente']] : [])] },
            { n: 'empresaIds', l: 'Empresas liberadas (vale só para o tipo Normal)', tipo: 'checkboxes',
                opcoes: async () => (await carregarEmpresas()).map(e => [e.id, e.razaoSocial]) }],
    },
};

// ---------- tela genérica: lista + formulário ----------

async function abrirTela(nome) {
    const cfg = TELAS[nome];
    document.querySelectorAll('#menu button').forEach(b =>
        b.dataset.tela === nome ? b.setAttribute('aria-current', 'page') : b.removeAttribute('aria-current'));
    aviso('');
    if (cfg.antes) await tentar(cfg.antes);

    const areaForm = el('div');
    const tabela = el('div');
    $('#conteudo').replaceChildren(
        el('h2', {}, cfg.titulo),
        valor(cfg.criar) !== false ? el('p', {}, el('button', { onclick: () => tentar(() => formulario(cfg, null, areaForm, recarregar)) }, 'Novo')) : null,
        areaForm, tabela);

    async function recarregar() {
        const itens = await api('GET', cfg.url);
        const podeEditar = cfg.editar === undefined || valor(cfg.editar);
        const excluir = valor(cfg.excluir);
        tabela.replaceChildren(el('table', {},
            el('thead', {}, el('tr', {}, cfg.colunas.map(([, l]) => el('th', {}, l)), el('th', {}, 'Ações'))),
            el('tbody', {}, itens.length === 0
                ? el('tr', {}, el('td', { colspan: cfg.colunas.length + 1 }, 'Nenhum registro.'))
                : itens.map(item => el('tr', {},
                    cfg.colunas.map(([n, , fmt]) => el('td', {}, fmt ? fmt(ler(item, n)) : (ler(item, n) ?? ''))),
                    el('td', {},
                        podeEditar && el('button', { onclick: () => tentar(() => formulario(cfg, item, areaForm, recarregar)) }, 'Editar'),
                        excluir && el('button', { class: 'perigo', onclick: () => confirm(`${excluir}?`) && tentar(async () => {
                            await api('DELETE', `${cfg.url}/${item.id}`);
                            await recarregar();
                        }, 'Feito.') }, excluir),
                        cfg.acoes?.(item, recarregar)))))));
    }

    await tentar(recarregar);
}

async function formulario(cfg, item, area, recarregar) {
    const campo = async (c) => {
        const valor = item ? (ler(item, c.n) ?? c.padrao ?? null) : (c.tipo === 'checkbox' ? true : c.padrao ?? null);
        let input;
        if (c.tipo === 'itemServico') return campoItemServico(c, valor);
        if (c.tipo === 'municipio') {
            return campoItemServico({ ...c, url: URL_MUNICIPIOS, rotulo: (m) => `${m.descricao} - ${m.grupo}`,
                placeholder: 'Buscar município pelo nome' }, valor);
        }
        if (c.tipo === 'checkboxes') {
            const marcados = (valor ?? []).map(String);
            const opcoes = await c.opcoes(item);
            return el('fieldset', { class: 'inteiro' }, el('legend', {}, c.l), opcoes.length ? opcoes.map(([v, r]) =>
                el('label', {}, el('input', { type: 'checkbox', name: c.n, value: v, checked: marcados.includes(String(v)) }), ' ', r))
                : 'Nenhuma empresa cadastrada.');
        }
        if (c.tipo === 'select') {
            input = el('select', { name: c.n, required: c.req }, el('option', { value: '' }, 'Selecione...'),
                (await c.opcoes(item)).map(([v, r]) => el('option', { value: v, selected: String(v) === String(valor) }, r)));
        } else {
            const mascara = MASCARAS[c.mascara];
            const numero = c.tipo === 'number';
            input = el('input', {
                name: c.n, type: c.tipo || 'text', required: c.req && !(item && c.tipo === 'password'),
                step: numero ? '0.01' : null, min: numero ? '0' : null, max: numero ? '9999999999999.99' : null,
                maxlength: mascara?.max ?? c.max, minlength: c.min, 'data-mascara': c.mascara, readonly: c.somenteLeitura,
                inputmode: c.mascara && c.mascara !== 'cnpj' && c.mascara !== 'documento' ? 'numeric' : null,
                checked: c.tipo === 'checkbox' && valor, value: c.tipo !== 'checkbox' && c.tipo !== 'password' ? (valor ?? '') : null,
            });
            mascarar(input); // formata também o valor vindo da API ao editar
        }
        return c.tipo === 'checkbox' ? el('label', {}, input, ' ', c.l) : el('label', {}, c.l, input);
    };

    const principais = cfg.campos.filter(c => !c.n.startsWith('endereco.'));
    const endereco = cfg.campos.filter(c => c.n.startsWith('endereco.'));
    const form = el('form', { class: 'cartao' },
        el('h3', {}, item ? 'Editar' : 'Novo'),
        el('div', { class: 'grade' }, await Promise.all(principais.map(campo))),
        endereco.length ? el('fieldset', {}, el('legend', {}, 'Endereço'), el('div', { class: 'grade' }, await Promise.all(endereco.map(campo)))) : null,
        el('button', {}, 'Salvar'), ' ',
        el('button', { type: 'button', class: 'secundario', onclick: () => area.replaceChildren() }, 'Fechar'));

    form.addEventListener('submit', (ev) => {
        ev.preventDefault();
        tentar(async () => {
            const corpo = paraObjeto(form, cfg.campos);
            await api(item ? 'PUT' : 'POST', item ? `${cfg.url}/${item.id}` : cfg.url, corpo);
            area.replaceChildren();
            await recarregar();
        }, 'Salvo.');
    });
    area.replaceChildren(form);
}

/** Converte o formulário no JSON da API: vazio vira null; campos "endereco.x" viram um objeto (ou null se todos vazios). */
function paraObjeto(form, campos) {
    const obj = {};
    for (const c of campos) {
        if (c.tipo === 'checkboxes') {
            obj[c.n] = [...form.querySelectorAll(`input[name="${c.n}"]:checked`)].map(i => Number(i.value));
            continue;
        }
        const input = form.elements[c.n];
        let v = c.tipo === 'checkbox' ? input.checked : input.value.trim();
        if (v === '') v = null;
        else if (c.tipo === 'number' || c.n.endsWith('Id')) v = Number(v);
        else if (MASCARAS[c.mascara]?.limpar) v = somenteAlfanum(v); // a API recebe CPF/CNPJ/CEP sem máscara
        const [pai, filho] = c.n.split('.');
        if (filho) (obj[pai] ??= {})[filho] = v;
        else obj[pai] = v;
    }
    if (obj.endereco && Object.values(obj.endereco).every(v => v == null)) obj.endereco = null;
    return obj;
}

const data = (v) => v ? v.split('-').reverse().join('/') : null;

/** Salva um texto como arquivo no computador do usuário (download gerado no navegador, sem nova chamada à API). */
function baixarArquivo(nome, texto) {
    const url = URL.createObjectURL(new Blob([texto], { type: 'application/xml' }));
    const link = el('a', { href: url, download: nome });
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Rótulo de uma opção fixa de um campo (ex.: tributacaoIssqn 1 -> "1 - Operação tributável"). */
async function rotuloOpcao(tela, campo, valor) {
    if (valor == null) return null;
    const opcoes = await TELAS[tela].campos.find(c => c.n === campo).opcoes();
    return opcoes.find(([v]) => String(v) === String(valor))?.[1] ?? String(valor);
}

/** Tela com todos os dados da nota (prestador, tomador, serviço, valores, tributação e emissão) e os XMLs. */
async function detalhesNota(id) {
    const [nota, emissao] = await Promise.all([
        api('GET', `/api/notas-fiscais/${id}`), api('GET', `/api/notas-fiscais/${id}/emissao`)]);
    const [empresa, cliente, servico, municipios, nacionais] = await Promise.all([
        api('GET', `/api/empresas/${claims().empresaId}`), api('GET', `/api/clientes/${nota.clienteId}`),
        api('GET', `/api/servicos/${nota.servicoId}`), carregarItensServico(URL_MUNICIPIOS),
        carregarItensServico('/api/itens-servico/nacionais')]);
    const municipio = (codigo) => {
        const m = municipios.find(i => i.codigo === codigo);
        return codigo ? (m ? `${m.descricao} - ${m.grupo} (${codigo})` : codigo) : null;
    };
    const endereco = (e) => e ? [e.logradouro, e.numero, e.complemento, e.bairro, municipio(e.codigoMunicipio),
        e.cep && MASCARAS.cep.formatar(e.cep)].filter(Boolean).join(', ') : null;
    const nacional = nacionais.find(i => i.codigo === nota.codigoTributacaoNacional);

    // cada bloco: [título, [[rótulo, valor], ...]]; linhas sem valor não aparecem
    const blocos = [
        ['Nota', [['Número', nota.numero], ['Status', statusNota(nota.status)], ['Ambiente', nota.ambiente],
            ['Criada em', dataHora(nota.dataCriacao)], ['Emitida em', dataHora(nota.dataEmissao)],
            ['Número da NFS-e', nota.numeroNfse], ['Chave de acesso', nota.codigoVerificacao],
            ['Data de competência', data(nota.dataCompetencia)], ['Município da prestação', municipio(nota.municipioPrestacao)]]],
        ['Prestador', [['Razão social', empresa.razaoSocial], ['CNPJ', MASCARAS.cnpj.formatar(empresa.cnpj)],
            ['Inscrição municipal', empresa.inscricaoMunicipal], ['Endereço', endereco(empresa.endereco)],
            ['Simples Nacional', await rotuloOpcao('empresas', 'opcaoSimplesNacional', empresa.opcaoSimplesNacional ?? 1)],
            ['Regime especial', await rotuloOpcao('empresas', 'regimeEspecialTributacao', empresa.regimeEspecialTributacao ?? 0)]]],
        ['Tomador', [['Nome', cliente.nome], ['CPF/CNPJ', cliente.documento && MASCARAS.documento.formatar(cliente.documento)],
            ['E-mail', cliente.email], ['Telefone', cliente.telefone], ['Endereço', endereco(cliente.endereco)]]],
        ['Serviço', [['Descrição', nota.descricao], ['Item municipal', servico.codigoServico],
            ['Código de tributação nacional', nacional ? `${nacional.codigo} - ${nacional.descricao}` : nota.codigoTributacaoNacional],
            ['Código NBS', servico.codigoNbs], ['Código da obra', nota.obraCodigo], ['Evento', nota.eventoNome],
            ['Período do evento', nota.eventoInicio && `${data(nota.eventoInicio)} a ${data(nota.eventoFim)}`],
            ['Código do evento', nota.eventoCodigo]]],
        ['Valores e tributação', [['Valor do serviço', moeda(nota.valorServico)], ['Desconto incondicionado', moeda(nota.valorDesconto)],
            ['Valor líquido', moeda(nota.valorLiquido)],
            ['Tributação do ISSQN', await rotuloOpcao('notas', 'tributacaoIssqn', nota.tributacaoIssqn)],
            ['Tipo de imunidade', await rotuloOpcao('notas', 'tipoImunidade', nota.tipoImunidade)],
            ['Retenção do ISSQN', await rotuloOpcao('notas', 'retencaoIssqn', nota.retencaoIssqn)],
            ['Alíquota do ISS', servico.aliquotaIss != null ? `${servico.aliquotaIss}%` : null],
            ['IBS/CBS: cIndOp', servico.indicadorOperacao], ['IBS/CBS: CST', servico.cstIbsCbs],
            ['IBS/CBS: cClassTrib', servico.classificacaoTributariaIbsCbs]]],
        ['Retorno da emissão', [['Mensagem', emissao.mensagemRetorno ?? 'Ainda não emitida.']]],
    ];

    const xml = (titulo, texto, arquivo) => texto && el('section', { class: 'cartao' },
        el('h3', {}, titulo),
        el('p', {}, el('button', { onclick: () => baixarArquivo(arquivo, texto) }, `Salvar ${arquivo}`)),
        el('pre', {}, texto));

    $('#conteudo').replaceChildren(
        el('h2', {}, `Nota fiscal ${nota.numero} `, statusNota(nota.status)),
        el('p', {}, el('button', { class: 'secundario', onclick: () => abrirTela('notas') }, 'Voltar para a lista')),
        el('div', { class: 'blocos' }, blocos.map(([titulo, linhas]) => el('section', { class: 'cartao' },
            el('h3', {}, titulo),
            el('dl', { class: 'dados' }, linhas.filter(([, v]) => v != null && v !== '')
                .flatMap(([rotulo, v]) => [el('dt', {}, rotulo), el('dd', {}, v)]))))),
        xml('XML da NFS-e (leiaute nacional; simulação, sem validade fiscal)', emissao.xmlNfse, `nfse-${nota.numeroNfse}.xml`),
        xml('XML da DPS', emissao.xmlDps, `dps-${nota.numero}.xml`));
    aviso('');
}

// ---------- sessão ----------

/** Monta o menu conforme o tipo do usuário e a empresa escolhida (o token traz os dois). */
async function entrar(novoToken) {
    token = novoToken;
    sessionStorage.setItem('token', token);
    $('#entrada').hidden = true;
    $('#app').hidden = false;
    $('#sair').hidden = false;
    $('#menu').replaceChildren(...Object.entries(TELAS)
        .filter(([, cfg]) => (!cfg.gestor || isGestor()) && (!cfg.empresa || temEmpresa()))
        .map(([nome, cfg]) => el('button', { 'data-tela': nome, onclick: () => abrirTela(nome) }, cfg.titulo)));
    await tentar(carregarEmpresas);
    const empresa = temEmpresa() ? nomesEmpresas.get(claims().empresaId) : 'nenhuma empresa selecionada';
    $('#usuario').textContent = `${empresa} · ${claims().role}`;
    if (token) await abrirTela(temEmpresa() ? 'notas' : 'empresas');
}

function sair() {
    token = null;
    sessionStorage.removeItem('token');
    $('#entrada').hidden = false;
    $('#app').hidden = true;
    $('#sair').hidden = true;
    $('#usuario').textContent = '';
}

$('#sair').addEventListener('click', () => { sair(); aviso(''); });

$('#form-login').addEventListener('submit', (ev) => {
    ev.preventDefault();
    const f = ev.target.elements;
    tentar(async () => entrar((await api('POST', '/api/auth/login', { email: f.email.value, senha: f.senha.value })).token));
});

if (token && claims()?.exp * 1000 > Date.now()) entrar(token);
else sair();
